CREATE TABLE IF NOT EXISTS codex_friends (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner CHAR(36) NOT NULL,
  target CHAR(36) NOT NULL,
  name VARCHAR(64) NOT NULL,
  dorm VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_friend (owner, target),
  KEY ix_friend_owner (owner, id),
  KEY ix_friend_target (target)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS codex_friend_owners (
  owner CHAR(36) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS codex_first_friend_signal (
  owner CHAR(36) NOT NULL PRIMARY KEY,
  delivered TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS codex_house_points (
  house TINYINT NOT NULL PRIMARY KEY,
  points BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO codex_house_points (house, points) VALUES (0,0),(1,0),(2,0),(3,0);

CREATE TABLE IF NOT EXISTS codex_donations (
  spell VARCHAR(64) NOT NULL PRIMARY KEY,
  spell_name VARCHAR(100) NOT NULL,
  donor CHAR(36) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  house TINYINT NOT NULL,
  created BIGINT NOT NULL,
  KEY ix_donations_order (created, spell)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS codex_houses (
  player CHAR(36) NOT NULL PRIMARY KEY,
  house TINYINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS codex_discovery_progress (
  player CHAR(36) NOT NULL,
  progress_key VARCHAR(128) NOT NULL,
  value DOUBLE NOT NULL,
  PRIMARY KEY (player, progress_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS codex_discovery_acquisitions (
  token BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  player CHAR(36) NOT NULL,
  spell VARCHAR(64) NOT NULL,
  first_discovery TINYINT NOT NULL,
  reward TINYINT NOT NULL,
  notified TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_acquisition (player, spell),
  KEY ix_acquisition_player (player, token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS codex_discovery_firsts (
  spell VARCHAR(64) NOT NULL PRIMARY KEY,
  player CHAR(36) NOT NULL,
  created BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS codex_player_state (
  player CHAR(36) NOT NULL PRIMARY KEY,
  circle TINYINT NOT NULL DEFAULT 1,
  mana_current DOUBLE NOT NULL DEFAULT 100,
  mana_maximum DOUBLE NOT NULL DEFAULT 100,
  mana_regeneration DOUBLE NOT NULL DEFAULT 5,
  magic_haste DOUBLE NOT NULL DEFAULT 0,
  mana_cooldowns TEXT NOT NULL,
  accessory_0 BLOB NULL,
  accessory_1 BLOB NULL,
  accessory_2 BLOB NULL,
  accessory_3 BLOB NULL,
  reconfig_credit_0 INT NOT NULL DEFAULT 0,
  reconfig_credit_1 INT NOT NULL DEFAULT 0,
  reconfig_credit_2 INT NOT NULL DEFAULT 0,
  lease_token CHAR(36) NULL,
  lease_until BIGINT NOT NULL DEFAULT 0,
  revision BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
