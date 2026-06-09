import { useTranslation } from 'react-i18next'

export default function LanguageSwitcher({ className = '' }) {
    const { i18n, t } = useTranslation()
    const current = i18n.language?.startsWith('hi') ? 'hi' : 'en'
    const toggle = () => i18n.changeLanguage(current === 'en' ? 'hi' : 'en')

    return (
        <button onClick={toggle} aria-label={t('language.switchTo')}
            className={`flex items-center gap-1 px-3 py-1.5 rounded-lg border border-primary-700/40 text-sm font-medium transition-all hover:border-amber-400/40 hover:text-amber-400 text-primary-300 ${className}`}>
            <span className={current === 'en' ? 'text-amber-400 font-bold' : 'text-primary-500'}>{t('language.en')}</span>
            <span className="text-primary-600">/</span>
            <span className={current === 'hi' ? 'text-amber-400 font-bold' : 'text-primary-500'}>{t('language.hi')}</span>
        </button>
    )
}
