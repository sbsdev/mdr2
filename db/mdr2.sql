-- drop all tables
DROP TABLE IF EXISTS production;
DROP TABLE IF EXISTS state;
DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS role;

-- State of a production
-- a classic reference table
CREATE TABLE state (
  id VARCHAR(16) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  next_state VARCHAR(16),
  FOREIGN KEY(next_state) REFERENCES state(id)
);

-- Meta data for a production
-- see http://www.daisy.org/z3986/2005/Z3986-2005.html#PubMed
CREATE TABLE production (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  creator VARCHAR(255),
  subject TEXT,
  description TEXT,
  publisher VARCHAR(255) NOT NULL DEFAULT "Swiss Library for the Blind, Visually Impaired and Print Disabled",
  date DATE NOT NULL,
  type VARCHAR(255),
  format VARCHAR(255),
  identifier VARCHAR(255) NOT NULL UNIQUE,
  source VARCHAR(255),
  language VARCHAR(10) NOT NULL DEFAULT "de",
  rights VARCHAR(64),
  source_date DATE,
  source_edition VARCHAR(255),
  source_publisher VARCHAR(255),
  source_rights VARCHAR(255),
  multimedia_type VARCHAR(25),
  multimedia_content VARCHAR(255),
  narrator VARCHAR(255),
  producer VARCHAR(255),
  produced_date DATE,
  revision VARCHAR(255),
  revision_date DATE,
  revision_description VARCHAR(255),
  total_time VARCHAR(255),
  audio_format VARCHAR(25),
  -- Number of levels
  depth TINYINT,
  -- SBS specific columns
  -- Number of volumes
  volumes TINYINT,
  -- when splitting a production manually we want to specify a
  -- specific sampling_rate and bitrate
  sampling_rate FLOAT,
  bit_rate INTEGER,
  -- initial state is "new"
  state VARCHAR(16) NOT NULL DEFAULT "new",
  -- production number given by the erp system. We should really use
  -- this as the primary key but alas some productions are done
  -- without involving the erp system, so we need to keep our own
  -- primary key
  product_number VARCHAR(255) UNIQUE,
  -- provisional number given to the production by the library system.
  -- There is only a libraryNumber if there is no productNumber, i.e.
  -- if this production is done behind the back of the erp system
  library_number VARCHAR(255) UNIQUE,
  -- The unique id that the library assigns to this production
  library_signature VARCHAR(255) UNIQUE,
  FOREIGN KEY(state) REFERENCES state(id)
);

CREATE TABLE user (
  id VARCHAR(32) PRIMARY KEY,
  first_name VARCHAR(255) NOT NULL,
  last_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL
);

CREATE TABLE role (
  id VARCHAR(32) PRIMARY KEY,
  name VARCHAR(255)
);

CREATE TABLE user_role (
  user_id VARCHAR(32) NOT NULL,
  role_id VARCHAR(32) NOT NULL,
  PRIMARY KEY (user_id, role_id),
  FOREIGN KEY(user_id) REFERENCES user(id),
  FOREIGN KEY(role_id) REFERENCES role(id)
);

SET FOREIGN_KEY_CHECKS=0;
INSERT INTO state (id, name, next_state) VALUES
("new", "New", "structured"),
("structured", "Structured", "recorded"),
("recorded", "Recorded", "encoded"),
("encoded", "Encoded", "cataloged"),
("cataloged", "Cataloged", "archived"),
("archived", "Archived", NULL),
("pending-split", "Pending volume split", "split"),
("split", "Split", "encoded"),
("failed", "Failed", NULL),
("deleted", "Deleted", NULL);
SET FOREIGN_KEY_CHECKS=1;

INSERT INTO production (title, creator, date, source, language, source_publisher, identifier) VALUES
("Unter dem Deich", "Hart, Maarten", "2014-12-12", "978-3-492-05573-4", "de", "Piper", "db010282-a39d-40b1-b5cf-f5285aa9b49d"),
("Aus dem Berliner Journal", "Frisch, Max", "2014-12-6", "978-3-518-42352-3", "de", "Suhrkamp", "504f26ef-7205-4b2f-b719-f4eef883ebe1"),
("Info-Express, Februar 2014", "SZB Taubblinden-Beratung", "2014-12-10", "", "de", "", "1492d357-6a6d-420f-b39e-b25178491b56");

INSERT INTO user (id, first_name, last_name, email, password) VALUES
("eglic", "Christian", "Egli", "christian.egli@sbs.ch", "$2a$10$go0rXWbX0IjhzkgjGKGf/uigHii6.bqTls.tjfQAsg9IdoSe.ouPq"),
("admin", "Super", "User", "admin@sbs.ch", "$2a$10$go0rXWbX0IjhzkgjGKGf/uigHii6.bqTls.tjfQAsg9IdoSe.ouPq");

INSERT INTO role (id, name) VALUES
("user", "Unprivileged User"),
("admin", "Administrator");

INSERT INTO user_role (user_id, role_id) VALUES
("eglic","user"),
("admin","user"),
("admin","admin");

SELECT * FROM production;
UPDATE production SET state = "encoded" WHERE id = 1;
UPDATE production SET volumes = 1 WHERE id = 1;
