import { useTranslation } from 'react-i18next'

const FACILITY_KEYS = ['wifi','ac','power','books','printing','refreshment','lockers','washrooms','cctv','quiet','parking','access']
const FACILITY_ICONS = { wifi:'💡', ac:'❄️', power:'🔌', books:'📚', printing:'🖨️', refreshment:'☕', lockers:'🔒', washrooms:'🚻', cctv:'📷', quiet:'🌿', parking:'🅿️', access:'♿' }

export default function FacilitiesPage() {
    const { t } = useTranslation()

    const facilities = FACILITY_KEYS.map(key => ({
        icon: FACILITY_ICONS[key],
        title: t(`facilities.items.${key}.title`),
        desc:  t(`facilities.items.${key}.desc`),
    }))

    const rules = t('facilities.rules.items', { returnObjects: true })

    const timings = [
        { key: 'morning', icon: '🌅' },
        { key: 'evening', icon: '🌆' },
        { key: 'fullDay', icon: '☀️' },
    ]

    const seatingRows = [
        { row: 'A', count: 28 },
        { row: 'B', count: 28 },
        { row: 'C', count: 28 },
        { row: 'D', count: 28 },
    ]

    return (
        <div>
            <div className="mb-8">
                <h1 className="page-header">{t('facilities.title')}</h1>
                <p className="text-primary-400">{t('facilities.subtitle')}</p>
            </div>

            <div className="card p-6 mb-8">
                <h2 className="section-title mb-5">{t('facilities.hours.title')}</h2>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                    {timings.map(({ key, icon }) => (
                        <div key={key} className="p-4 rounded-xl bg-primary-800/40 border border-primary-700/30 text-center">
                            <div className="text-3xl mb-2">{icon}</div>
                            <p className="text-white font-semibold">{t(`facilities.hours.${key}.label`)}</p>
                            <p className="text-amber-400 font-mono text-sm mt-1">{t(`facilities.hours.${key}.time`)}</p>
                            <p className="text-primary-500 text-xs mt-2">{t(`facilities.hours.${key}.plan`)}</p>
                        </div>
                    ))}
                </div>
                <p className="text-primary-500 text-xs mt-4 text-center">{t('facilities.hours.openNote')}</p>
            </div>

            <div className="mb-8">
                <h2 className="section-title mb-5">{t('facilities.offer')}</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    {facilities.map(({ icon, title, desc }) => (
                        <div key={title} className="card p-5 hover:border-amber-500/20 transition-colors">
                            <div className="text-2xl mb-3">{icon}</div>
                            <h3 className="text-white font-semibold mb-2">{title}</h3>
                            <p className="text-primary-400 text-sm leading-relaxed">{desc}</p>
                        </div>
                    ))}
                </div>
            </div>

            <div className="card p-6 mb-8">
                <h2 className="section-title mb-2">{t('facilities.seating.title')}</h2>
                <p className="text-primary-400 text-sm mb-5">{t('facilities.seating.subtitle')}</p>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                    {seatingRows.map(({ row, count }) => (
                        <div key={row} className="p-4 rounded-xl bg-primary-800/40 border border-primary-700/30 text-center">
                            <div className="w-10 h-10 rounded-full bg-amber-500/20 border border-amber-500/30 text-amber-400 font-bold text-lg flex items-center justify-center mx-auto mb-2">{row}</div>
                            <p className="text-white font-semibold">{t('facilities.seating.seats', { count })}</p>
                            <p className="text-primary-500 text-xs mt-1">{t(`facilities.seating.rows.${row}`)}</p>
                        </div>
                    ))}
                </div>
            </div>

            <div className="card p-6">
                <h2 className="section-title mb-5">{t('facilities.rules.title')}</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {rules.map((rule, i) => (
                        <div key={i} className="flex items-start gap-3 p-3 rounded-xl bg-primary-800/30">
                            <span className="text-amber-400 font-bold text-sm mt-0.5">{i + 1}.</span>
                            <p className="text-primary-300 text-sm leading-relaxed">{rule}</p>
                        </div>
                    ))}
                </div>
                <div className="mt-5 p-4 rounded-xl bg-red-500/10 border border-red-500/20">
                    <p className="text-red-400 text-sm font-medium">{t('facilities.rules.violation')}</p>
                </div>
            </div>
        </div>
    )
}
