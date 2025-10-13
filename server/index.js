// server/index.js
import express from "express";
import pkg from "pg";
const { Pool } = pkg;

const app = express();
const pool = new Pool({
  user: process.env.DATABASE_USER,
  host: "localhost",
  database: "call_log",
  password: process.env.DATABASE_PASSWORD,
  port: 5432,
});

app.use(express.json());

// Get rows
app.get("/api/rows", async (req, res) => {
  const result = await pool.query("SELECT * FROM call_log_names");
  console.log("get");
  res.json(result.rows);
});

// Update row
app.put("/api/rows/:id", async (req, res) => {
  console.log("put");
  const { id } = req.params;
  const { column, value } = req.body;
  sql = `UPDATE call_log_names set ${column} = $1 where id = $2`, [value, id];
  await pool.query(`UPDATE call_log_names SET ${column} = $1 WHERE id = $2`, [value, id]);
  console.log(sql);
  res.sendStatus(200);
});

app.listen(4000, () => console.log("Server running on http://localhost:4000"));

