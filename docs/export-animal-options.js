// 국가동물보호정보시스템 발견신고 작성 화면의 Chrome 개발자 도구 콘솔에서 실행하세요.
(async () => {
  'use strict';
  if (location.hostname !== 'www.animal.go.kr' && location.hostname !== 'animal.go.kr')
    throw Error('국가동물보호정보시스템의 신고 작성 화면에서 실행하세요.');
  if (window.__animalOptionsRunning) throw Error('이미 수집 중입니다.');

  const all = () => [...document.querySelectorAll('select')];
  const label = s => [
    s.title, s.getAttribute('aria-label'), s.name, s.id,
    ...Array.from(s.labels || [], l => l.textContent),
    s.closest('tr')?.querySelector('th')?.textContent,
  ].filter(Boolean).join(' ');
  const one = (xs, name) => {
    if (xs.length !== 1) throw Error(name + ' 선택 상자를 식별하지 못했습니다. 발견신고 작성 화면인지 확인하세요.');
    return xs[0];
  };
  const species = one(all().filter(s =>
    ['개', '고양이'].every(name => [...s.options].some(o => o.text.trim() === name))), '축종');
  const findBreed = () => {
    const rest = all().filter(s => s !== species);
    const row = species.closest('tr');
    const sameRow = row ? rest.filter(s => row.contains(s)) : [];
    return one(sameRow.length === 1 ? sameRow : rest.filter(s => /품종|kindcd|breed/i.test(label(s))), '품종');
  };
  const findColor = () => one(all().filter(s => /색상|모색|털색|color|colour/i.test(label(s))), '색상');
  const options = s => [...s.options].filter(o => o.value.trim() && !o.disabled &&
    !o.parentElement?.matches('optgroup:disabled') && !o.hidden && getComputedStyle(o).display !== 'none' &&
    !/^(선택|전체|선택하세요|선택해주세요)$/.test(o.text.replace(/[\s*.-]/g, '')))
    .map(o => ({code: o.value, name: o.text.trim()}));
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const change = (s, code) => {
    s.value = code;
    if (s.value !== code) throw Error('선택할 수 없는 코드: ' + code);
    s.dispatchEvent(new Event('change', {bubbles: true}));
  };
  // 값이 같은 두 품종도 각각 목록을 다시 채웠는지 확인한다.
  async function load(parent, code, child) {
    const old = child();
    let touched = false, last = 0;
    const obs = new MutationObserver(records => {
      const current = child();
      if (current !== old || records.some(r => current.contains(r.target) &&
          (r.type !== 'attributes' || r.target.matches('option,optgroup')))) {
        touched = true;
        last = performance.now();
      }
    });
    obs.observe(document.body, {subtree:true, childList:true, attributes:true, characterData:true});
    try {
      change(parent, code);
      const start = performance.now();
      while (performance.now() - start < 7000) {
        await sleep(100);
        if (parent.value !== code) throw Error('수집 중 선택값이 바뀌었습니다.');
        const s = child(), rows = options(s);
        if (touched && !s.disabled && rows.length && performance.now() - last >= 350 &&
            !(window.jQuery?.active > 0)) return rows;
      }
      throw Error('하위 목록 갱신을 확인할 수 없음');
    } finally { obs.disconnect(); }
  }
  const original = [species.value, findBreed().value, findColor().value];
  const result = {source: location.origin + location.pathname, extractedAt: new Date().toISOString(),
    complete:false, species:[], issues:[]};
  const report = (s, b, e) => result.issues.push({speciesCode:s, breedCode:b, reason:e.message});
  window.__animalOptionsRunning = true;
  try {
    for (const sp of options(species)) {
      const entry = {...sp, breeds:null};
      result.species.push(entry);
      let breeds;
      try { breeds = await load(species, sp.code, findBreed); }
      catch (e) { report(sp.code, null, e); continue; }
      entry.breeds = [];
      for (const [i, breed] of breeds.entries()) {
        const item = {...breed, colors:null};
        entry.breeds.push(item);
        try { item.colors = await load(findBreed(), breed.code, findColor); }
        catch (e) { report(sp.code, breed.code, e); }
        console.info(sp.name + ' ' + (i+1) + '/' + breeds.length + ' ' + breed.name,
          item.colors === null ? '미확인' : item.colors.length + '개 색상');
      }
    }
    result.complete = result.species.length > 0 && result.issues.length === 0;
    window.animalOptionsResult = result;
    const blob = new Blob([JSON.stringify(result, null, 2)], {type:'application/json;charset=utf-8'});
    const url = URL.createObjectURL(blob), a = document.createElement('a');
    a.href = url;
    a.download = 'animal-options' + (result.complete ? '' : '-partial') + '.json';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
    console.info(a.download, '미확인 항목:', result.issues.length);
  } finally {
    try {
      if (original[0]) await load(species, original[0], findBreed);
      else change(species, original[0]);
      if (original[1]) await load(findBreed(), original[1], findColor);
      else if (findBreed().value !== original[1]) change(findBreed(), original[1]);
      if ([...findColor().options].some(o => o.value === original[2])) change(findColor(), original[2]);
    } catch (e) { console.warn('원래 선택값을 확인하세요:', e.message); }
    delete window.__animalOptionsRunning;
  }
})();
