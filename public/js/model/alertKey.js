define(['backbone'], function(Backbone) {
    return Backbone.Model.extend({
        defaults: {
            id: null,
            type: null,
            label: null,
            value: null
        }
    });
});