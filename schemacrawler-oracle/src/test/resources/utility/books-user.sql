-- Create Oracle User With Just Schema Object Access Role

CREATE USER BOOKSUSER IDENTIFIED BY booksuser;
GRANT CONNECT TO BOOKSUSER;

-- Run the grants from this SQL statement
SELECT 
 'GRANT ' ||
  CASE 
    WHEN OBJECT_TYPE IN ('SEQUENCE', 'SYNONYM') THEN 'SELECT'
    WHEN OBJECT_TYPE IN ('TABLE', 'VIEW') THEN 'SELECT, INSERT, UPDATE, DELETE'
    WHEN OBJECT_TYPE IN ('FUNCTION', 'PROCEDURE') THEN 'EXECUTE'
  END
  || ' ON BOOKS."' || OBJECT_NAME || '" TO BOOKSUSER;'
FROM 
  DBA_OBJECTS 
WHERE 
  OBJECT_TYPE IN ('TABLE', 'VIEW', 'PROCEDURE', 'FUNCTION', 'SEQUENCE', 'SYNONYM') 
  AND OWNER='BOOKS'; 
