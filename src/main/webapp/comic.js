

var COMIC_ROOT = "estrips/";

function isInput (v)
{
    return (typeof v !== 'undefined') && (v !== null);
}

function sendComicData(protocol, path, data, contentType, callback)
{
    var async = isInput(callback);
    var xmlHttp = new XMLHttpRequest();
    xmlHttp.open(protocol, COMIC_ROOT + path, async);
    if (isInput(contentType))
        xmlHttp.setRequestHeader('Content-Type', contentType);
    if (async)
        xmlHttp.onload = function () { callback(this.responseText); };
    xmlHttp.send(data);
    if (xmlHttp.status >= 300)
        throw new Error(xmlHttp.status + " (" + xmlHttp.statusText + ")");
    return xmlHttp.responseText;
}

//-------------- METADATA --------------------
function Metadata (){}

Metadata.getMetadata = function()
{
    return jQuery.parseJSON(sendComicData("GET", "meta", null));
};

Metadata.createMetadata = function(meta)
{
    sendComicData("POST", "meta", meta);
};

Metadata.deleteMetadata = function(meta)
{
    sendComicData("POST", "meta/delete", meta);
};

Metadata.getLanguages = function()
{
    return jQuery.parseJSON(sendComicData("GET", "languages", null));
};

Metadata.createLanguage = function(lang)
{
    sendComicData("POST", "languages", lang);
};

Metadata.deleteLanguage = function(lang)
{
    sendComicData("POST", "languages/delete", lang);
};

Metadata.getPredicates = function()
{
    return jQuery.parseJSON(sendComicData("GET", "predicates", null));
};

Metadata.createPredicate = function(predicate)
{
    sendComicData("POST", "predicates", predicate);
};

Metadata.deletePredicate = function(predicate)
{
    sendComicData("POST", "predicates/delete", predicate);
};

Metadata.enrich = function(text, callback)
{
    return sendComicData("POST", "enrich", text, null, callback);
};

//-------------- COMIC --------------------
function Comic (id)
{
    this.pages = [];
    this.title = "";
    this.metadata = {};
    if (id)
    {
        this.id = id;
        var response = sendComicData("GET", "comic/" + this.id, null);
        
        var comicJSON = jQuery.parseJSON(response);
        var comic = this;
        $.each(comicJSON.pages, function(index, pageJSON)
        {
            comic.addPageByData(pageJSON);
        });
        comic.metadata = comicJSON.metadata;
    }
    else
    {
        this.id = parseInt(sendComicData("POST", "comic", null));
    }
}

Comic.listComics = function()
{
    var comics = sendComicData("GET", "comic", null);
    return comics;
};

Comic.prototype.getEPUBURL = function()
{
    return COMIC_ROOT + "comic/" + this.id + "/epub";
};

Comic.prototype.destroy = function()
{
    sendComicData("DELETE", "comic/" + this.id, null);
};

Comic.prototype.addMetadata = function(key, val)
{
    if (val.length > 0 && key.length > 0)
    {
        if (!(key in this.metadata))
            this.metadata[key] = [];
        if (this.metadata[key].indexOf(val) < 0)
        {
            this.metadata[key].push(val);
            sendComicData("POST", "comic/" + this.id + "/meta/" + key, val);
        }
    }
};

Comic.prototype.deleteMetadata = function(key, val)
{
    if (val.length > 0 && key.length > 0 && (key in this.metadata))
    {
        var idx = this.metadata[key].indexOf(val);
        if (idx >= 0)
        {
            this.metadata[key].splice(idx, 1);
            sendComicData("POST", "comic/" + this.id + "/meta/" + key + "/delete/", val);
        }
    }
};

Comic.prototype.setMetadata = function(key, val)
{
    if (key in this.metadata)
    {
        for (var i = 0; i < this.metadata[key].length; ++i)
            this.deleteMetadata(key, this.metadata[key][i]);
    }
    if (val.length > 0)
        this.addMetadata(key, val);
};

Comic.prototype.addPageByImg = function(imgUint8Array)
{
    var page = new Page();
    page.id = parseInt(sendComicData("POST", "comic/" + this.id + "/page", null));
    page.imgID = parseInt(sendComicData("POST", "page/" + page.id + "/img", imgUint8Array));
    page.comic = this;
    this.pages.push(page);
    
    return page;
};

Comic.prototype.addPageByData = function(pageJSON)
{
    var page = new Page();
    page.id = pageJSON.id;
    page.imgID = pageJSON.img;
    page.comic = this;
    this.pages.push(page);

    $.each(pageJSON.panels, function(index, panelJSON)
    {
        page.addPanelByData(panelJSON);
    });
    return page;
};

Comic.prototype.getPageByID = function(id)
{
    for (var i = 0; i < this.pages.length; ++i)
    {
        var page = this.pages[i];
        if (page.id === id)
            return page;
    }
    return null;
};

Comic.prototype.movePage = function(from, to)
{
    sendComicData("POST", "page/" + this.pages[from].id + "/position", to+"");
    var page = this.pages[from];
    this.pages.splice(from, 1);
    this.pages.splice(to, 0, page);
};

Comic.prototype.setPagesByIDs = function(ids)
{
    var comic = this;
    if (ids.length === this.pages.length && ids.every(function (id, idx) { return id === comic.pages[idx].id; }))
        return;
    sendComicData("POST", "comic/" + this.id + "/page/order", JSON.stringify(ids), "application/json");
    var pages = ids.map(function(id) { return comic.getPageByID(id); });
    this.pages = pages;
};

Comic.prototype.deletePage = function(idx)
{
    if (idx < 0 || idx >= this.pages.length)
        return;
    sendComicData("DELETE", "page/" + this.pages[idx].id, null);
    this.pages.splice(idx, 1);
};

// -------------- PAGE --------------------
function Page()
{
    this.id = 0;
    this.imgID = 0;
    this.comic = null;
    this.panels = [];
}

Page.prototype.getImgURL = function()
{
    return COMIC_ROOT + "img/" + this.imgID;
};

Page.prototype.generateRects = function()
{
    var rects = jQuery.parseJSON(sendComicData("GET", "img/" + this.imgID + "/rects", null));
    
    var page = this;
    $.each(rects, function(index, rect)
    {
        page.addPanelByValues(rect.x, rect.y, rect.width, rect.height);
    });
};

Page.prototype.addPanelByValues = function(x, y, width, height)
{
    var panel = new Panel();
    panel.page = this;
    panel.rect = {x: x, y: y, width: width, height: height};
    panel.id = parseInt(sendComicData("POST", "page/" + this.id + "/panel", JSON.stringify(panel.rect), "application/json"));
    this.panels.push(panel);
    return panel;
};

Page.prototype.addPanelByData = function(panelJSON)
{
    var panel = new Panel();
    panel.id = panelJSON.id;
    panel.page = this;
    panel.rect = {x: panelJSON.x, y: panelJSON.y, width: panelJSON.width, height: panelJSON.height};
    panel.metadata = panelJSON.metadata;
    panel.texts = panelJSON.texts;
    this.panels.push(panel);
    
    return panel;
};

Page.prototype.movePanel = function(from, to)
{
    sendComicData("POST", "panel/" + this.panels[from].id + "/position", to+"");
    var panel = this.panels[from];
    this.panels.splice(from, 1);
    this.panels.splice(to, 0, panel);
};

//-------------- PANEL --------------------
function Panel()
{
    this.id = 0;
    this.page = null;
    this.rect = {x: 0, y: 0, width: 0, height: 0};
    this.metadata = [];
    this.texts = [];
}

Panel.prototype.destroy = function()
{
    sendComicData("DELETE", "panel/" + this.id, null);
};

Panel.prototype.addMetadata = function(meta)
{
    var idx = this.metadata.indexOf(meta);
    if (idx < 0)
    {
        this.metadata.push(meta);
        sendComicData("POST", "panel/" + this.id + "/meta", meta);
    }
};

Panel.prototype.deleteMetadata = function(meta)
{
    var idx = this.metadata.indexOf(meta);
    if (idx >= 0)
    {
        this.metadata.splice(idx, 1);
        sendComicData("POST", "panel/" + this.id + "/meta/delete", meta);
    }
};

Panel.prototype.addAction = function(action)
{
    var idx = this.actions.indexOf(action);
    if (idx < 0)
    {
        this.actions.push(action);
        sendComicData("POST", "panel/" + this.id + "/action", action);
    }
};

Panel.prototype.deleteAction = function(action)
{
    var idx = this.actions.indexOf(action);
    if (idx >= 0)
    {
        this.actions.splice(idx, 1);
        sendComicData("POST", "panel/" + this.id + "/action/delete", action);
    }
};

Panel.prototype.addText = function(lang, predicate, text)
{
    var textObj = {lang: lang, predicate: predicate, text: text};
    var idx = this.texts.indexOf(textObj);
    if (idx >= 0)
        return;
    this.texts.push(textObj);
    sendComicData("POST", "panel/" + this.id + "/text", JSON.stringify(textObj), "application/json");
};

Panel.prototype.deleteText = function(idx)
{
    if (idx < 0 || idx >= this.texts.length)
        return;
    var result = this.texts.splice(idx, 1);
    sendComicData("POST", "panel/" + this.id + "/text/delete", JSON.stringify(result[0]), "application/json");
};

Panel.prototype.resize = function(x, y, width, height)
{
    if (x != this.rect.x || y != this.rect.y || width != this.rect.width || height != this.rect.height)
    {
        this.rect = {x: x, y: y, width: width, height: height};
        sendComicData("POST", "panel/" + this.id + "/rect", JSON.stringify(this.rect), "application/json");
    }
};