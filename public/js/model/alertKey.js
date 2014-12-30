define(['backbone'], function(Backbone) {
    return Backbone.Model.extend({
        defaults: {
            id: null,
            type_id: null,
            label: null,
            value: null
        }
    });
});