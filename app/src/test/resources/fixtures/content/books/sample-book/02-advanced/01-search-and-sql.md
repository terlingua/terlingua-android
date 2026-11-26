---
title: Search and SQL
questions:
  - id: fts
    prompt: Which SQLite module powers full-text search here?
    options:
      - text: FTS5
        correct: true
      - text: R-Tree
---
```sql
SELECT title FROM search_index WHERE search_index MATCH 'query*';
```
