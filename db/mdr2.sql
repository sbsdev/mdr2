-- drop all tables
DROP TABLE IF EXISTS volume;
DROP TABLE IF EXISTS production;
DROP TABLE IF EXISTS state;
DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS role;

-- State of a production
-- a classic reference table
CREATE TABLE state (
  id TINYINT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  next_state_id TINYINT,
  FOREIGN KEY(next_state_id) REFERENCES state(id)
);

-- Meta data for a production
-- see http://www.daisy.org/z3986/2005/Z3986-2005.html#PubMed
CREATE TABLE production (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  creator VARCHAR(255),
  subject TEXT,
  description TEXT,
  publisher VARCHAR(255) NOT NULL,
  date DATE NOT NULL,
  type VARCHAR(255),
  format VARCHAR(255),
  identifier VARCHAR(255) NOT NULL UNIQUE,
  source VARCHAR(255),
  language VARCHAR(10) NOT NULL,
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
  state_id TINYINT NOT NULL,
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
  FOREIGN KEY(state_id) REFERENCES state(id)
);

-- each production has at least one volume
CREATE TABLE volume (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  production_id INTEGER NOT NULL,
  sort_order TINYINT,
  FOREIGN KEY(production_id) REFERENCES production(id)
);

CREATE TABLE user (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(255) NOT NULL UNIQUE,
  first_name VARCHAR(255) NOT NULL,
  last_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL
);

CREATE TABLE role (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE user_role (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  user_id INTEGER NOT NULL,
  role_id INTEGER NOT NULL,
  FOREIGN KEY(user_id) REFERENCES user(id),
  FOREIGN KEY(role_id) REFERENCES role(id)
);

SET FOREIGN_KEY_CHECKS=0;
INSERT INTO state (id, name, next_state_id) VALUES
(0, "new", 1),
(1, "structured", 2),
(2, "recorded", 3),
(3, "encoded", 4),
(4, "cataloged", 5),
(5, "archived", NULL),
(6, "pending-volume-split", 3),
(7, "failed", NULL),
(8, "deleted", NULL);
SET FOREIGN_KEY_CHECKS=1;

INSERT INTO production (title, creator, source, language, source_publisher, state_id) VALUES
("Unter dem Deich", "Hart, Maarten", "978-3-492-05573-4", "de", "Piper", 0),
("Aus dem Berliner Journal", "Frisch, Max", "978-3-518-42352-3", "de", "Suhrkamp", 0),
("Info-Express, Februar 2014", "SZB Taubblinden-Beratung", "", "de", "", 0);

INSERT INTO user (username, first_name, last_name, email, password) VALUES 
("eglic", "Christian", "Egli", "christian.egli@sbs.ch", "$2a$10$go0rXWbX0IjhzkgjGKGf/uigHii6.bqTls.tjfQAsg9IdoSe.ouPq"),
("admin", "Super", "User", "admin@sbs.ch", "$2a$10$go0rXWbX0IjhzkgjGKGf/uigHii6.bqTls.tjfQAsg9IdoSe.ouPq");

INSERT INTO role (name) VALUES 
("User"),
("Admin");

INSERT INTO user_role (user_id, role_id) VALUES 
(1,1),
(2,1),
(2,2);

SELECT * FROM production;
