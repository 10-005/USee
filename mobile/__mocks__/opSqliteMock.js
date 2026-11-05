/* eslint-env node */
// Exercise production SQL on real in-memory SQLite; only the RN native bridge is replaced.
const nativeRequire = require('node:module').createRequire(__filename);
const { DatabaseSync } = nativeRequire('node:sqlite');

module.exports = {
  open: () => {
    const db = new DatabaseSync(':memory:');
    return {
      executeSync: (sql, params = []) => {
        const statement = db.prepare(sql);
        if (statement.columns().length) {
          return { rows: statement.all(...params), rowsAffected: 0 };
        }
        return { rows: [], rowsAffected: Number(statement.run(...params).changes) };
      },
      close: () => db.close(),
    };
  },
};
