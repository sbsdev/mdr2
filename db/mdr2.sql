CREATE TABLE productions (
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
  -- SBS specific columns 
  state TEXT,
  productNumber TEXT
);

INSERT INTO productions (title, creator, source, language, sourcePublisher) VALUES 
("Unter dem Deich", "Hart, Maarten", "978-3-492-05573-4", "de", "Piper"),
("Aus dem Berliner Journal", "Frisch, Max", "978-3-518-42352-3", "de", "Suhrkamp"),
("Info-Express, Februar 2014", "SZB Taubblinden-Beratung", "", "de", "");
