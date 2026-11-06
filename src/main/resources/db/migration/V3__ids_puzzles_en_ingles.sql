-- Los ids de puzzle pasan al inglés, como en la app. Las claves, los campos y los estados no cambian.
-- puzzles_estado_puzzle_check es el nombre que Postgres le dio al CHECK de V1 (comprobado en Postgres 17).

ALTER TABLE puzzles_estado DROP CONSTRAINT puzzles_estado_puzzle_check;

UPDATE puzzles_estado
   SET puzzle = CASE puzzle
                  WHEN 'fotografia'   THEN 'photo'
                  WHEN 'mirador'      THEN 'lookout'
                  WHEN 'observatorio' THEN 'observatory'
                  ELSE puzzle
                END
 WHERE puzzle IN ('fotografia', 'mirador', 'observatorio');

ALTER TABLE puzzles_estado
  ADD CONSTRAINT puzzles_estado_puzzle_check
  CHECK (puzzle IN ('radio', 'photo', 'lookout', 'observatory'));
