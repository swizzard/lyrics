-- :name create-albums-table :!
-- :doc Create albums table
create table albums (
  id bigint auto_increment,
  name text,
  short_name varchar(250),
  year smallint unsigned,
  constraint albums_pk primary key(id),
  constraint album_name unique (short_name))

-- :name drop-albums-table :!
-- :doc Drop albums table
drop table if exists albums

-- :name create-artists-table :!
-- :doc Create artists table
create table artists (
  id bigint auto_increment,
  name text,
  short_name varchar(250),
  constraint artists_pk primary key(id),
  constraint artist_name unique (short_name))

-- :name drop-artists-table :!
-- :doc Drop artists table
drop table if exists artists

-- :name create-songs-table :!
-- :doc Create songs table
create table songs (
  id bigint auto_increment,
  name text,
  short_name varchar(250),
  album_id bigint,
  track_no int,
  index song_album (short_name, album_id),
  constraint songs_pk primary key(id),
  constraint songs_albums_fk foreign key(album_id) references albums(id),
  constraint song_name_album unique (short_name, album_id))

-- :name drop-songs-table :!
-- :doc Drop songs table
drop table if exists songs

-- :name create-songs-artists-table :!
-- :doc Create songs_artists M2M table
create table songs_artists (
  id bigint auto_increment,
  song_id bigint not null,
  artist_id bigint not null,
  featuring boolean,
  constraint songs_artists_pk primary key(id),
  constraint songs_artists_songs_fk foreign key(song_id) references songs(id) on delete cascade,
  constraint songs_artists_artists_fk foreign key(artist_id) references artists(id) on delete cascade)

-- :name drop-songs-artists-table :!
-- :doc Drop songs_artists M2M table
drop table if exists songs_artists

-- :name create-tokens-table :!
-- :doc Create tokens table
create table tokens (
  id bigint auto_increment,
  artist_id bigint,
  song_id bigint not null,
  lemma varchar(250),
  pos varchar(250),
  value varchar(250) not null,
  line_no integer unsigned not null,
  idx integer unsigned not null,
  is_eol boolean not null default 0,
  index tkn_idx (song_id, line_no, is_eol, idx),
  index tkn_val (value),
  constraint unique tkn_constraint (song_id, line_no, idx),
  constraint tokens_pk primary key(id),
  constraint tokens_artists_fk foreign key(artist_id) references artists(id) on delete cascade,
  constraint tokens_songs_fk foreign key(song_id) references songs(id) on delete cascade)

-- :name drop-tokens-table :!
-- :doc Drop tokens table
drop table if exists tokens

