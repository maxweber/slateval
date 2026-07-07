var slateval = {};
slateval.db = {};

/**
 * @constructor
 */
slateval.db.Datom = function() {};
slateval.db.Datom.prototype.e;
slateval.db.Datom.prototype.a;
slateval.db.Datom.prototype.v;
slateval.db.Datom.prototype.tx;
slateval.db.Datom.prototype.idx;

slateval.impl = {};
slateval.impl.entity = {};

/**
 * @constructor
 */
slateval.impl.entity.Entity = function() {};
slateval.impl.entity.Entity.prototype.db;
slateval.impl.entity.Entity.prototype.eid;
slateval.impl.entity.Entity.prototype.keys      = function() {};
slateval.impl.entity.Entity.prototype.entries   = function() {};
slateval.impl.entity.Entity.prototype.values    = function() {};
slateval.impl.entity.Entity.prototype.has       = function() {};
slateval.impl.entity.Entity.prototype.get       = function() {};
slateval.impl.entity.Entity.prototype.forEach   = function() {};
slateval.impl.entity.Entity.prototype.key_set   = function() {};
slateval.impl.entity.Entity.prototype.entry_set = function() {};
slateval.impl.entity.Entity.prototype.value_set = function() {};
