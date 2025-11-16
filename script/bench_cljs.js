#! node

global.performance = {
  now: function () {
         var t = process.hrtime();
         return t[0] * 1000 + t[1] / 1000000;
       }
}

require("../target/dbval.js");

dbval.bench.dbval._main(...process.argv.slice(2));