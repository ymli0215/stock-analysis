/**
 * Shared top navbar — call initNavbar('page-key') after jQuery loads.
 * Valid keys: index, stock-gate, stock-turn, future-bbi, stock-exclude
 */
function initNavbar(currentPage) {
    var pages = [
        { key: 'index',         href: 'index.html',         label: '整合首頁' },
        { key: 'stock-gate',    href: 'stock-gate.html',    label: '多空戰K' },
        { key: 'stock-turn',    href: 'stock-turn.html',    label: '多空中軸' },
        { key: 'future-bbi',    href: 'future-bbi.html',    label: '多空趨勢' },
        { key: 'stock-exclude', href: 'stock-exclude.html', label: '除權息' }
    ];

    var items = '';
    pages.forEach(function (p) {
        var active = (p.key === currentPage) ? ' class="active"' : '';
        items += '<li' + active + '><a href="' + p.href + '">' + p.label + '</a></li>';
    });

    var html = [
        '<nav class="navbar navbar-default navbar-static-top" role="navigation" style="margin-bottom:0">',
        '<div class="container-fluid">',
        '<div class="navbar-header">',
        '<button type="button" class="navbar-toggle" data-toggle="collapse" data-target="#top-navbar-collapse">',
        '<span class="sr-only">Toggle navigation</span>',
        '<span class="icon-bar"></span><span class="icon-bar"></span><span class="icon-bar"></span>',
        '</button>',
        '<a class="navbar-brand" href="index.html">&#128200; StockApp</a>',
        '</div>',
        '<div class="collapse navbar-collapse" id="top-navbar-collapse">',
        '<ul class="nav navbar-nav">' + items + '</ul>',
        '</div>',
        '</div>',
        '</nav>'
    ].join('');

    $('#navbar-placeholder').replaceWith(html);
}
