// Lightweight phonetic Latin -> Devanagari transliteration.
//
// Used only for *display* — showing Roman-script student names in Devanagari
// when the UI language is Hindi. Roman spellings of Indian names are
// inherently ambiguous (t/ट vs त, final schwa, etc.), so this is a best
// effort that gets the common cases right; it never touches stored data.

const INDEP = {
    aa: 'आ', ai: 'ऐ', au: 'औ', ii: 'ई', ee: 'ई', oo: 'ऊ', uu: 'ऊ',
    a: 'अ', i: 'इ', u: 'उ', e: 'ए', o: 'ओ',
}
const MATRA = {
    aa: 'ा', ai: 'ै', au: 'ौ', ii: 'ी', ee: 'ी', oo: 'ू', uu: 'ू',
    a: '', i: 'ि', u: 'ु', e: 'े', o: 'ो',
}
// Consonants after which a preceding "n"/"m" reads as anusvara
// (Sanjay -> संजय, Sandeep -> संदीप) rather than a half-letter.
const ANUSVARA_BEFORE = new Set(['k', 'kh', 'g', 'gh', 'ch', 'chh', 'j', 'jh',
    't', 'th', 'd', 'dh', 'p', 'ph', 'b', 'bh', 's', 'sh', 'ss'])
const CONS = {
    ksh: 'क्ष', chh: 'छ', gy: 'ज्ञ',
    kh: 'ख', gh: 'घ', ng: 'ंग', ch: 'च', jh: 'झ', th: 'थ', dh: 'ध',
    ph: 'फ', bh: 'भ', sh: 'श', ss: 'ष',
    k: 'क', g: 'ग', c: 'क', j: 'ज', t: 'त', d: 'द', n: 'न', p: 'प',
    f: 'फ', b: 'ब', m: 'म', y: 'य', r: 'र', l: 'ल', v: 'व', w: 'व',
    s: 'स', h: 'ह', z: 'ज़', q: 'क', x: 'क्स',
}
const VIRAMA = '्'

// Longest keys first so multi-letter clusters win over their prefixes.
const CONS_KEYS = Object.keys(CONS).sort((a, b) => b.length - a.length)
const VOWEL_KEYS = Object.keys(MATRA).sort((a, b) => b.length - a.length)

const matchAt = (str, i, keys) => keys.find(k => str.startsWith(k, i))

function word(w) {
    let out = ''
    let i = 0
    while (i < w.length) {
        const c = matchAt(w, i, CONS_KEYS)
        if (c) {
            i += c.length
            const v = matchAt(w, i, VOWEL_KEYS)
            if (v) {
                i += v.length
                // A trailing Roman "a" on a name is almost always a long
                // aa sound in practice (Priya, Neha, Sita) — not a schwa.
                const longFinalA = v === 'a' && i === w.length
                out += CONS[c] + (longFinalA ? 'ा' : MATRA[v])
            } else if (i === w.length) {
                // Drop the inherent schwa at word end (Hindi convention).
                out += CONS[c]
            } else if ((c === 'n' || c === 'm') && ANUSVARA_BEFORE.has(matchAt(w, i, CONS_KEYS))) {
                out += 'ं'
            } else {
                // A conjunct — mark the half-letter with virama.
                out += CONS[c] + VIRAMA
            }
            continue
        }
        const v = matchAt(w, i, VOWEL_KEYS)
        if (v) {
            i += v.length
            out += INDEP[v]
            continue
        }
        out += w[i]
        i += 1
    }
    return out
}

export function toDevanagari(name) {
    if (!name || typeof name !== 'string') return name
    // Already contains Devanagari — leave it alone.
    if (/[ऀ-ॿ]/.test(name)) return name
    if (!/[a-zA-Z]/.test(name)) return name
    return name
        .split(/(\s+)/)
        .map(seg => (/\s/.test(seg) || !seg ? seg : word(seg.toLowerCase())))
        .join('')
}
