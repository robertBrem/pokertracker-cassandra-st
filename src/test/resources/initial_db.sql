CREATE KEYSPACE pokertracker WITH REPLICATION = { 'class' : 'SimpleStrategy','replication_factor' : 3 };

USE pokertracker;

CREATE TABLE EVENTS (
 ID bigint,
 NAME text,
 VERSION bigint,
 DATE timestamp,
 DATA text,
 PRIMARY KEY(ID, NAME, VERSION)
);
