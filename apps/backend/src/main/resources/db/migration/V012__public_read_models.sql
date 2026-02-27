-- Public read models for versioned schedule/results payload delivery.

CREATE TABLE public_regatta_draw (
    regatta_id UUID NOT NULL,
    draw_revision INTEGER NOT NULL DEFAULT 0,
    entry_id UUID NOT NULL,
    event_id UUID NOT NULL,
    bib INTEGER,
    lane INTEGER,
    scheduled_start_time TIMESTAMPTZ,
    crew_name VARCHAR(255) NOT NULL,
    club_name VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'entered',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (regatta_id, draw_revision, entry_id)
);

CREATE INDEX idx_draw_regatta_rev ON public_regatta_draw(regatta_id, draw_revision);
CREATE INDEX idx_draw_event ON public_regatta_draw(event_id);
CREATE INDEX idx_draw_scheduled ON public_regatta_draw(scheduled_start_time);

CREATE TABLE public_regatta_results (
    regatta_id UUID NOT NULL,
    draw_revision INTEGER NOT NULL DEFAULT 0,
    results_revision INTEGER NOT NULL DEFAULT 0,
    entry_id UUID NOT NULL,
    event_id UUID NOT NULL,
    bib INTEGER,
    crew_name VARCHAR(255) NOT NULL,
    club_name VARCHAR(255),
    elapsed_time_ms INTEGER,
    penalties_ms INTEGER DEFAULT 0,
    rank INTEGER,
    status VARCHAR(50) NOT NULL DEFAULT 'entered',
    is_provisional BOOLEAN DEFAULT TRUE,
    is_edited BOOLEAN DEFAULT FALSE,
    is_official BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (regatta_id, draw_revision, results_revision, entry_id)
);

CREATE INDEX idx_results_regatta_rev ON public_regatta_results(regatta_id, draw_revision, results_revision);
CREATE INDEX idx_results_event ON public_regatta_results(event_id);
CREATE INDEX idx_results_status ON public_regatta_results(status);
CREATE INDEX idx_results_rank ON public_regatta_results(rank);
