-- Athletes read model for BC03-004 (H2 compatible)
-- Reference: pdd/design/database-schema.md

CREATE TABLE clubs (
    id UUID PRIMARY KEY,
    name CHARACTER VARYING(255) NOT NULL,
    short_name CHARACTER VARYING(50),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_clubs_name ON clubs(name);
CREATE INDEX idx_clubs_short_name ON clubs(short_name);

CREATE TABLE athletes (
    id UUID PRIMARY KEY,
    first_name CHARACTER VARYING(100) NOT NULL,
    middle_name CHARACTER VARYING(100),
    last_name CHARACTER VARYING(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender CHARACTER VARYING(20) NOT NULL CHECK (gender IN ('M', 'F', 'X')),
    club_id UUID REFERENCES clubs(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_athletes_club ON athletes(club_id);
CREATE INDEX idx_athletes_first_name ON athletes(first_name);
CREATE INDEX idx_athletes_last_name ON athletes(last_name);
CREATE INDEX idx_athletes_name_search ON athletes(first_name, last_name);

CREATE TABLE crews (
    id UUID PRIMARY KEY,
    display_name CHARACTER VARYING(255) NOT NULL,
    is_composite BOOLEAN NOT NULL DEFAULT FALSE,
    club_id UUID REFERENCES clubs(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_crews_display_name ON crews(display_name);
CREATE INDEX idx_crews_club ON crews(club_id);
CREATE INDEX idx_crews_composite ON crews(is_composite);

CREATE TABLE crew_athletes (
    id UUID PRIMARY KEY,
    crew_id UUID NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
    athlete_id UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
    seat_position INTEGER NOT NULL CHECK (seat_position >= 1),
    UNIQUE(crew_id, athlete_id),
    UNIQUE(crew_id, seat_position)
);

CREATE INDEX idx_crew_athletes_crew ON crew_athletes(crew_id);
CREATE INDEX idx_crew_athletes_athlete ON crew_athletes(athlete_id);
