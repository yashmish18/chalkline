-- V6: departments come from the uploaded allocation sheet, not from a seed list.
-- V1 seeded seven departments; remove every one that no faculty member or course refers to.
-- New departments are created by the importer from the sheet's Department column.

DELETE FROM department d
WHERE NOT EXISTS (SELECT 1 FROM faculty f WHERE f.department_id = d.id)
  AND NOT EXISTS (SELECT 1 FROM course c WHERE c.department_id = d.id);
