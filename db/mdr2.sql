-- Meta data for a production
-- see http://www.daisy.org/z3986/2005/Z3986-2005.html#PubMed
CREATE TABLE production (
  id INTEGER PRIMARY KEY,
  title TEXT,
  creator TEXT,
  subject TEXT,
  description TEXT,
  publisher TEXT,
  date TEXT,
  type TEXT,
  format TEXT,
  identifier TEXT,
  source TEXT,
  language TEXT,
  rights TEXT,
  sourceDate TEXT,
  sourceEdition TEXT,
  sourcePublisher TEXT,
  sourceRights TEXT,
  multimediaType TEXT,
  multimediaContent TEXT,
  narrator TEXT,
  producer TEXT,
  producedDate TEXT,
  revision TEXT,
  revisionDate TEXT,
  revisionDescription TEXT,
  totalTime TEXT,
  audioFormat TEXT,
  -- Number of CDs
  volumes INTEGER,
  -- Number of levels
  depth INTEGER,
  -- SBS specific columns
  state INTEGER,
  -- production number given by the erp system. We should really use
  -- this as the primary key but alas some productions are done
  -- without involving the erp system, so we need to keep our own
  -- primary key
  productNumber TEXT,
  -- provisional number given to the production by the library system.
  -- There is only a libraryNumber if there is no productNumber, i.e.
  -- if this production is done behind the back of the erp system
  libraryNumber TEXT,
  -- The unique id that the library assigns to this production
  librarySignature TEXT
  -- FIXME: seems backwards that the production system has to know the
  -- id of the library system. Alas in order to be able to archive we
  -- need to know it. Otherwise apparently the library cannot find
  -- anything anymore in the archive :-/
);

CREATE TABLE product (
  id INTEGER PRIMARY KEY,
  identifier TEXT NOT NULL,
  type INTEGER NOT NULL,
  production_id INTEGER NOT NULL,
  FOREIGN KEY(production_id) REFERENCES production(id)
);

CREATE TABLE user (
  id INTEGER PRIMARY KEY,
  username TEXT NOT NULL,
  first_name TEXT NOT NULL,
  last_name TEXT NOT NULL,
  email TEXT NOT NULL,
  password TEXT NOT NULL
);

CREATE TABLE role (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL
);

CREATE TABLE user_role (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL,
  role_id INTEGER NOT NULL,
  FOREIGN KEY(user_id) REFERENCES user(id),
  FOREIGN KEY(role_id) REFERENCES role(id)
);


INSERT INTO production (title, creator, source, language, sourcePublisher, state) VALUES
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

