import { describe, it, expect, beforeEach } from 'vitest';
import { useFormatting } from '../composables/useFormatting';

describe('useFormatting', () => {
  let formatting;

  beforeEach(() => {
    formatting = useFormatting();
  });

  describe('formatDateISO', () => {
    it('formats date in ISO 8601 format', () => {
      const date = new Date(2026, 1, 6, 12, 0, 0);
      expect(formatting.formatDateISO(date)).toBe('2026-02-06');
    });

    it('handles null input', () => {
      expect(formatting.formatDateISO(null)).toBe('');
    });

    it('handles invalid date', () => {
      expect(formatting.formatDateISO('invalid')).toBe('');
    });
  });

  describe('formatScheduledTime', () => {
    it('formats time in 24-hour format', () => {
      const time = new Date(2026, 1, 6, 14, 30, 0);
      const formatted = formatting.formatScheduledTime(time);
      expect(formatted).toBe('14:30');
    });

    it('handles null input', () => {
      expect(formatting.formatScheduledTime(null)).toBe('');
    });
  });

  describe('formatElapsedTime', () => {
    it('formats time under 1 hour as M:SS.mmm', () => {
      const ms = (5 * 60 * 1000) + (23 * 1000) + 456; // 5:23.456
      expect(formatting.formatElapsedTime(ms)).toBe('5:23.456');
    });

    it('formats time over 1 hour as H:MM:SS.mmm', () => {
      const ms = (1 * 60 * 60 * 1000) + (15 * 60 * 1000) + (42 * 1000) + 789; // 1:15:42.789
      expect(formatting.formatElapsedTime(ms)).toBe('1:15:42.789');
    });

    it('pads zeros correctly', () => {
      const ms = (2 * 1000) + 45; // 0:02.045
      expect(formatting.formatElapsedTime(ms)).toBe('0:02.045');
    });

    it('handles zero time', () => {
      expect(formatting.formatElapsedTime(0)).toBe('0:00.000');
    });

    it('handles null input', () => {
      expect(formatting.formatElapsedTime(null)).toBe('');
    });
  });

  describe('formatDeltaTime', () => {
    it('formats leader as +0:00.000', () => {
      expect(formatting.formatDeltaTime(0)).toBe('+0:00.000');
    });

    it('formats delta with + prefix', () => {
      const ms = (1 * 60 * 1000) + (15 * 1000) + 234; // +1:15.234
      expect(formatting.formatDeltaTime(ms)).toBe('+1:15.234');
    });

    it('formats large delta', () => {
      const ms = (1 * 60 * 60 * 1000) + (5 * 60 * 1000) + (30 * 1000) + 500; // +1:05:30.500
      expect(formatting.formatDeltaTime(ms)).toBe('+1:05:30.500');
    });
  });

  describe('roundTime', () => {
    it('rounds using half-up rule', () => {
      expect(formatting.roundTime(1234.567, 0)).toBe(1235);
      expect(formatting.roundTime(1234.445, 0)).toBe(1234);
    });

    it('handles precision parameter', () => {
      expect(formatting.roundTime(1234.567, 2)).toBe(1234.57);
      expect(formatting.roundTime(1234.567, 1)).toBe(1234.6);
    });

    it('handles null input', () => {
      expect(formatting.roundTime(null)).toBe(0);
    });
  });

  describe('formatTimestampISO', () => {
    it('formats timestamp in ISO 8601 format', () => {
      const date = new Date('2026-02-06T14:30:00Z');
      const formatted = formatting.formatTimestampISO(date, 'UTC');
      expect(formatted).toBe('2026-02-06T14:30:00+00:00');
    });

    it('handles null input', () => {
      expect(formatting.formatTimestampISO(null)).toBe('');
    });
  });
});
