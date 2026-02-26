package com.regattadesk.linescan;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled task for evaluating and executing line-scan retention pruning.
 * 
 * Implements BC06-006 retention scheduler:
 * - Runs periodically (default: hourly)
 * - Evaluates manifests in active retention states
 * - Transitions states based on delay and safety gates
 * - Emits admin alerts when delay elapses before gates satisfied
 * - Executes pruning for eligible manifests
 * 
 * Resilient to individual manifest failures - continues processing others.
 */
@ApplicationScoped
public class LineScanRetentionScheduler {
    
    private static final Logger LOG = Logger.getLogger(LineScanRetentionScheduler.class);
    
    private final LineScanManifestRepository manifestRepository;
    private final LineScanRetentionEvaluator evaluator;
    private final LineScanPruningService pruningService;
    private final RegattaRepository regattaRepository;
    private final EntryRepository entryRepository;
    private final TimingMarkerRepository markerRepository;
    
    public LineScanRetentionScheduler(
        LineScanManifestRepository manifestRepository,
        LineScanRetentionEvaluator evaluator,
        LineScanPruningService pruningService,
        RegattaRepository regattaRepository,
        EntryRepository entryRepository,
        TimingMarkerRepository markerRepository
    ) {
        this.manifestRepository = manifestRepository;
        this.evaluator = evaluator;
        this.pruningService = pruningService;
        this.regattaRepository = regattaRepository;
        this.entryRepository = entryRepository;
        this.markerRepository = markerRepository;
    }
    
    /**
     * Evaluates and prunes line-scan manifests based on retention policy.
     * Scheduled to run hourly (configurable via Quarkus config).
     * 
     * Can be disabled by setting:
     * quarkus.scheduler.enabled=false
     * or by disabling this specific schedule in application.properties
     */
    @Scheduled(
        cron = "{linescan.retention.scheduler.cron:0 0 * * * ?}",
        identity = "linescan-retention-evaluator"
    )
    public void evaluateAndPrune() {
        LOG.info("Starting line-scan retention evaluation");
        
        Instant now = Instant.now();
        
        // Find all manifests that need evaluation (not PRUNED)
        List<LineScanManifest.RetentionState> activeStates = List.of(
            LineScanManifest.RetentionState.FULL_RETAINED,
            LineScanManifest.RetentionState.PENDING_DELAY,
            LineScanManifest.RetentionState.ELIGIBLE_WAITING_ARCHIVE_OR_APPROVALS
        );
        
        List<LineScanManifest> manifests = manifestRepository.findByRetentionStateIn(activeStates);
        
        if (manifests.isEmpty()) {
            LOG.info("No manifests require retention evaluation");
            return;
        }
        
        LOG.infof("Evaluating %d manifests for retention", manifests.size());
        
        int transitioned = 0;
        int pruned = 0;
        int alerted = 0;
        int errors = 0;
        
        for (LineScanManifest manifest : manifests) {
            try {
                ActionResult result = evaluateAndPruneManifest(manifest, now);
                switch (result) {
                    case PRUNED -> pruned++;
                    case TRANSITIONED -> transitioned++;
                    case ALERTED -> alerted++;
                    case NO_ACTION -> {}
                }
            } catch (Exception e) {
                LOG.errorf(e, "Error evaluating manifest %s, continuing with others", manifest.getId());
                errors++;
            }
        }
        
        LOG.infof("Line-scan retention evaluation complete: %d transitioned, %d pruned, %d alerted, %d errors",
            transitioned, pruned, alerted, errors);
    }
    
    private enum ActionResult {
        NO_ACTION,
        TRANSITIONED,
        PRUNED,
        ALERTED
    }
    
    /**
     * Evaluates and processes a single manifest.
     * 
     * @param manifest The manifest to evaluate
     * @param now Current timestamp for evaluation
     * @return ActionResult indicating what action was taken
     */
    @Transactional
    protected ActionResult evaluateAndPruneManifest(LineScanManifest manifest, Instant now) {
        // Check safety gates
        boolean regattaArchived = regattaRepository.isArchived(manifest.getRegattaId());
        boolean allEntriesApproved = entryRepository.areAllEntriesApprovedForRegatta(manifest.getRegattaId());
        
        // Evaluate retention state
        LineScanRetentionEvaluator.EvaluationResult result = evaluator.evaluate(
            manifest,
            regattaArchived,
            allEntriesApproved,
            now
        );
        
        // Handle alert condition
        if (result.getAlertReason() != null) {
            emitAdminAlert(manifest, result.getAlertReason());
            return ActionResult.ALERTED;
        }
        
        // Handle state transition
        if (result.shouldTransitionState()) {
            transitionManifestState(manifest, result);
            return ActionResult.TRANSITIONED;
        }
        
        // Handle pruning
        if (result.shouldPrune()) {
            executeManifestPruning(manifest);
            return ActionResult.PRUNED;
        }
        
        // No action needed
        return ActionResult.NO_ACTION;
    }
    
    private void transitionManifestState(
        LineScanManifest manifest,
        LineScanRetentionEvaluator.EvaluationResult result
    ) {
        LOG.infof("Transitioning manifest %s from %s to %s",
            manifest.getId(),
            manifest.getRetentionState(),
            result.getTargetState());
        
        LineScanManifest updated = LineScanManifest.builder()
            .id(manifest.getId())
            .regattaId(manifest.getRegattaId())
            .captureSessionId(manifest.getCaptureSessionId())
            .tileSizePx(manifest.getTileSizePx())
            .primaryFormat(manifest.getPrimaryFormat())
            .fallbackFormat(manifest.getFallbackFormat())
            .xOriginTimestampMs(manifest.getXOriginTimestampMs())
            .msPerPixel(manifest.getMsPerPixel())
            .tiles(manifest.getTiles())
            .retentionDays(manifest.getRetentionDays())
            .pruneWindowSeconds(manifest.getPruneWindowSeconds())
            .retentionState(result.getTargetState())
            .pruneEligibleAt(result.getPruneEligibleAt() != null
                ? result.getPruneEligibleAt()
                : manifest.getPruneEligibleAt())
            .prunedAt(manifest.getPrunedAt())
            .createdAt(manifest.getCreatedAt())
            .updatedAt(Instant.now())
            .build();
        
        manifestRepository.save(updated);
    }
    
    private void executeManifestPruning(LineScanManifest manifest) {
        LOG.infof("Executing pruning for manifest %s", manifest.getId());
        
        // Get approved markers for this regatta
        List<TimingMarker> approvedMarkers = markerRepository.findApprovedByRegattaId(manifest.getRegattaId());
        
        // Calculate marker windows
        List<LineScanRetentionEvaluator.TimeWindow> markerWindows = 
            evaluator.getMarkerWindows(approvedMarkers, manifest.getPruneWindowSeconds());
        
        LOG.infof("Manifest %s: found %d approved markers, calculated %d preservation windows",
            manifest.getId(), approvedMarkers.size(), markerWindows.size());
        
        // Execute pruning
        pruningService.prune(manifest, markerWindows);
    }
    
    private void emitAdminAlert(LineScanManifest manifest, String alertReason) {
        // Log alert at WARN level for operational visibility
        LOG.warnf("ADMIN ALERT: Manifest %s (regatta %s) - %s",
            manifest.getId(),
            manifest.getRegattaId(),
            alertReason);
        
        // In production, this would also:
        // - Insert record into admin_alerts table
        // - Emit monitoring metric/event
        // - Send notification to configured channels
        // For BC06-006 v0.1, operational logging is sufficient
    }
}
