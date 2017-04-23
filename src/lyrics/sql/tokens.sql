-- :name insert-artist! :insert
-- :doc insert an artist
insert ignore into artists (name, short_name)
values (:name, :short-name)

-- :name get-artist-id
-- :result :one
-- :doc get an artist's id by short-name
select id from artists where short_name = :short-name

-- :name insert-song! :insert
-- :doc insert a song
insert ignore into songs (name, short_name, album_id)
values (:name, :short-name, :album-id)

-- :name get-song-id
-- :result :one
-- :doc get a song's id by short-name
select id from songs where short_name = :short-name

-- :name insert-album! :insert
-- :doc insert an album
insert ignore into albums (name, short_name)
values (:name, :short-name)

-- :name get-album-id
-- :result :one
-- :doc get an album's id by short-name
select id from albums where short_name = :short-name

-- :name insert-token! :insert
-- :doc insert a token and most relationships
start transaction;
insert into tokens (album_id, song_id, value, line_no, idx, is_eol, section)
            values (:album-id, :song-id, :value, :line-no, :idx, :is-eol, :section);
commit;

-- :name insert-song-artist! :insert
-- :doc insert song-artists
insert ignore songs_artists (song_id, artist_id, featuring) values (:song-id, :artist-id,
                                                                    :featuring)

-- :name last-n-in-line
-- :doc get n-gram of last tokens in a line
select t.id, t.value, t.idx from tokens t
join tokens t2
  on t.song_id = t2.song_id
  and t.line_no = t2.line_no
where t2.is_eol = true
  and t2.line_no = :line-no
  and t2.song_id = :song-id
  and t.idx between (select t2.idx - :n + 1) and t2.idx
