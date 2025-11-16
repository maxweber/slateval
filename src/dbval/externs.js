var dbval = {};
dbval.db = {};

/**
 * @constructor
 */
dbval.db.Datom = function() {};
dbval.db.Datom.prototype.e;
dbval.db.Datom.prototype.a;
dbval.db.Datom.prototype.v;
dbval.db.Datom.prototype.tx;
dbval.db.Datom.prototype.idx;

dbval.impl = {};
dbval.impl.entity = {};

/**
 * @constructor
 */
dbval.impl.entity.Entity = function() {};
dbval.impl.entity.Entity.prototype.db;
dbval.impl.entity.Entity.prototype.eid;
dbval.impl.entity.Entity.prototype.keys      = function() {};
dbval.impl.entity.Entity.prototype.entries   = function() {};
dbval.impl.entity.Entity.prototype.values    = function() {};
dbval.impl.entity.Entity.prototype.has       = function() {};
dbval.impl.entity.Entity.prototype.get       = function() {};
dbval.impl.entity.Entity.prototype.forEach   = function() {};
dbval.impl.entity.Entity.prototype.key_set   = function() {};
dbval.impl.entity.Entity.prototype.entry_set = function() {};
dbval.impl.entity.Entity.prototype.value_set = function() {};
