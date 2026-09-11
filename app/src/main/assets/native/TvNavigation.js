(() => {
'use strict';
if (window.JellyfinWebTv) return;
const selector = 'button:not([disabled]),a[href],[tabindex]:not([tabindex="-1"]),[role="button"],[role="menuitem"],[role="tab"],input:not([disabled]),select:not([disabled])';
let current = null;
function visible(el) {
  const r = el.getBoundingClientRect();
  return r.width > 3 && r.height > 3 && !el.closest('[hidden],[aria-hidden="true"],[inert]') &&
    getComputedStyle(el).visibility !== 'hidden';
}
function items() {
  const dialogs = Array.from(document.querySelectorAll('dialog[open],[role="dialog"],.dialog')).filter(visible);
  const root = dialogs[dialogs.length - 1] || document;
  return Array.from(root.querySelectorAll(selector)).filter(visible);
}
function mark(el) {
  if (!el) return false;
  if (current) current.classList.remove('webtv-focus');
  current = el;
  el.classList.add('webtv-focus');
  el.focus({preventScroll:true});
  el.scrollIntoView({block:'nearest',inline:'nearest',behavior:'auto'});
  return true;
}
const style = document.createElement('style');
style.textContent = '.webtv-focus {outline:3px solid #00a4dc!important;outline-offset:3px!important;}';
document.head.appendChild(style);
window.JellyfinWebTv = {
handleKey(command) {
  const all = items();
  if (!all.length) return false;
  if (!current || !all.includes(current)) mark(all[0]);
  if (command === 'select') {current.click();return true;}
  const a = current.getBoundingClientRect();
  const vertical = command === 'up' || command === 'down';
  const sign = command === 'up' || command === 'left' ? -1 : 1;
  let best = null, score = Infinity;
  for (const el of all) {
    if (el === current || el.contains(current) || current.contains(el)) continue;
    const b = el.getBoundingClientRect();
    const dx = b.left+b.width/2-a.left-a.width/2;
    const dy = b.top+b.height/2-a.top-a.height/2;
    const primary = vertical ? dy : dx, secondary = vertical ? dx : dy;
    if (primary*sign <= 3) continue;
    const s = Math.abs(primary)+2.8*Math.abs(secondary);
    if (s < score) {score=s;best=el;}
  }
  return mark(best);
}};
})();