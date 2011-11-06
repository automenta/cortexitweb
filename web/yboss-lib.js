/*
  YBOSS by Christian Heilmann
  Version: 1.0
  Homepage: http://icant.co.uk/sandbox/yboss/
  Copyright (c) 2008, Christian Heilmann
  Code licensed under the BSD License:
  http://wait-till-i.com/license.txt
*/
YBOSS = function(){
  var config = {
    webItemHTML:'<li><a href="{clickurl}">{title}</a><p>{abstract}</p></li>',
    newsItemHTML:'<li lang="{language}"><a href="{clickurl}">{title}</a><p>{abstract} ({source})</p></li>',
    imageItemHTML:'<li><a href="{clickurl}">{title}</a><a href="{url}"><img src="{thumbnail}" width="{thumbnailwidth}" height="{thumbnailheight}"></a><p>{shortened}</p></li>'
  }
  var appID = '4muP0fTV34EwDebJKZAXcJ8rM' + 
              '.HrcyjGnNiihCuezlmEa_aNBJv.vcXaln98qow-';
  var queries = [];
  function get(o){
    if(typeof o !== 'undefined' && 
       typeof o.searches === 'string' && 
       typeof o.query === 'string' &&
       typeof o.callback === 'function'){
      var searches = makeAPIurl(o);
      o.query = encodeURIComponent(o.query);
      queries[o.query] = {
        all:o.searches.split(',').length,
        count:0,
        databack:{},
        callback:o.callback,
        fail: o.fail
      };
      add(searches);
    }
  }
  function add(urls){
    for(var i=0;i<urls.length;i++){
      var s = document.createElement('script');
      s.setAttribute('src',urls[i]);
      s.setAttribute('type','text/javascript');
      document.getElementsByTagName('head')[0].appendChild(s);
    }
  }
  function makeAPIurl(o){
      var searchtypes = [];
      var APIurl = 'http://boss.yahooapis.com/ysearch/web/v1/' + 
                   clean(o.query) +'?format=json&callback=YBOSS.retrieved&' +
                   'appid=' + appID;
      if(typeof o.count === 'number' && 
         parseInt(o.count) === o.count && o.count > 0){
        APIurl += '&count=' + o.count;
      }
      if(typeof o.start === 'number' && 
         parseInt(o.start) === o.start && o.start > 0){
        APIurl += '&start=' + o.start;
      }
      if(typeof o.sites === 'string'){
        APIurl += '&sites=' + clean(o.sites);
      }
      if(typeof o.filter === 'string'){
        APIurl += '&filter=' + clean(o.filter);
      }
      if(typeof o.lang === 'string'){
        APIurl += '&lang=' + clean(o.lang);
      }
      if(typeof o.region === 'string'){
        APIurl += '&region=' + clean(o.region);
      }
      if(typeof o.type === 'string'){
        APIurl += '&type=' + clean(o.type);
      }
      if(typeof o.dimensions === 'string'){
        APIurl += '&dimensions=' + clean(o.dimensions);
      }
      if(typeof o.refererurl === 'string'){
        APIurl += '&refererurl=' + clean(o.refererurl);
      }
      if(typeof o.url === 'string'){
        APIurl += '&url=' + clean(o.url);
      }
      if(typeof o.age === 'string'){
        APIurl += '&age=' + clean(o.age);
      }
      if(typeof o.filter === 'string'){
        APIurl += '&filter=' + clean(o.filter);
      }
      if(typeof o.sites === 'string'){
        APIurl += '&sites=' + clean(o.sites);
      }
      if(o.searches.indexOf('search')!==-1){
        searchtypes.push(APIurl);
      }
      if(o.searches.indexOf('images')!==-1){
        searchtypes.push(APIurl.replace('web','images'));
      }
      if(o.searches.indexOf('news')!==-1){
        searchtypes.push(APIurl.replace('web','news'));
      }
      return searchtypes;
  }
  function clean(s){
    return encodeURIComponent(s);
  }
  function retrieved(o){
    if(typeof o.ysearchresponse.nextpage !== 'undefined'){
      var next = o.ysearchresponse.nextpage.split('?')[0];
      var query = next.replace(/.*?\//g,'');
    } 
    if(typeof o.ysearchresponse.prevpage !== 'undefined'){
      var last = o.ysearchresponse.prevpage.split('?')[0];
      var query = last.replace(/.*?\//g,'');
    } 
    queries[query].count++;
    queries[query].databack.query = query;
    databack = queries[query].databack;
    if(typeof o.ysearchresponse.resultset_web !== 'undefined'){
      databack.web = [];
      databack.webHTML = '<ul>';
      for(var i=0,j=o.ysearchresponse.resultset_web.length;i<j;i++){
        var item = o.ysearchresponse.resultset_web[i];
        databack.web.push(
          {
            abstract:item.abstract,
            title:item.title,
            url:item.clickurl,
            displayurl:item.dispurl
          }
        );
        var html = config.webItemHTML.replace('{clickurl}',item.clickurl);
        html = html.replace('{title}',item.title);
        html = html.replace('{abstract}',item.abstract);
        databack.webHTML += html;
      }
      databack.webHTML += '</ul>';
    }
    if(typeof o.ysearchresponse.resultset_images !== 'undefined'){
      databack.images = [];
      databack.imagesHTML = '<ul>';
      for(var i=0,j=o.ysearchresponse.resultset_images.length;i<j;i++){
        var item = o.ysearchresponse.resultset_images[i];
        var referer = item.refererurl;
        var shorter = referer.replace('http://www.','').substring(0,39);
        databack.images.push(
          {
            abstract:item.abstract,
            title:item.title,
            url:item.clickurl,
            page:item.refererclickurl,
            pagedisplay:item.refererurl,
            shorturl:shorter+'&hellip;',
            filename:item.filename,
            imageurl:item.url,
            thumbnail:item.thumnail_url,
            thumbnaildimensions:[item.thumbnail_width,item.thumbnail_height],
            dimensions:[item.width,item.height],
            format:item.format
          }
        );
        var html = config.imageItemHTML.replace('{url}',item.url);
        html = html.replace('{clickurl}',item.refererclickurl);
        html = html.replace('{shortened}',shorter);
        html = html.replace('{thumbnail}',item.thumbnail_url);
        html = html.replace('{thumbnailwidth}',item.thumbnail_width);
        html = html.replace('{thumbnailheight}',item.thumbnail_height);
        html = html.replace('{title}',item.title);
        databack.imagesHTML += html;
      }
      databack.imagesHTML += '</ul>';
    }
    if(typeof o.ysearchresponse.resultset_news !== 'undefined'){
      databack.news = [];
      databack.newsHTML = '<ul>';
      for(var i=0,j=o.ysearchresponse.resultset_news.length;i<j;i++){
        var item = o.ysearchresponse.resultset_news[i];
        databack.news.push(
          {
            abstract:item.abstract,
            title:item.title,
            url:item.clickurl,
            language:item.language,
            source:item.source,
            sourceurl:item.sourceurl
          }
        );
        var html = config.newsItemHTML.replace('{language}',item.language);
        html = html.replace('{clickurl}',item.clickurl);
        html = html.replace('{abstract}',item.abstract);
        html = html.replace('{title}',item.title);
        html = html.replace('{source}',item.source);
        html = html.replace('{sourceurl}',item.sourceurl);
        databack.newsHTML += html;
      }
      databack.newsHTML += '</ul>';
    }
    if(queries[query].count === queries[query].all){
       queries[query].callback(databack);
    }
    else  {
       queries[query].fail();
    }
  }
  return{
    get:get,
    config:config,
    retrieved:retrieved,
    appID:appID
  }
}();
