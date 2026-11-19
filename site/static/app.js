(function(){
var q=document.getElementById('q');
var grid=document.getElementById('app-grid');
var none=document.getElementById('no-results');
if(!q||!grid)return;
var cards=Array.prototype.slice.call(grid.querySelectorAll('.card.app'));
q.addEventListener('input',function(){
var v=q.value.trim().toLowerCase();
var shown=0;
cards.forEach(function(c){
var ok=!v||(c.getAttribute('data-search')||'').indexOf(v)>=0;
c.hidden=!ok;
if(ok)shown++;
});
if(none)none.hidden=shown>0||cards.length===0;
});
})();
