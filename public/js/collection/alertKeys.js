define(['backbone', 'model/alertKey'], function(Backbone, AlertKey) {
    return Backbone.Collection.extend({
        model: AlertKey
    });
});