import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

function SettingField({ label, value, onChange, integer = false, prefix }) {
    return (
        <div>
            <label className="label">{label}</label>
            <div className="flex items-center gap-1">
                {prefix && <span className="text-primary-400">{prefix}</span>}
                <input
                    type="number"
                    min="0"
                    step={integer ? '1' : '0.01'}
                    value={value}
                    onChange={e => onChange(e.target.value)}
                    className="input w-full"
                />
            </div>
        </div>
    )
}

// Frontend-only reference config — labels/descriptions/preview text for
// translation guidance. Does not affect what's actually sent; the English
// wording lives in notification-service's NotificationService.java.
// `adminEditable`/`hindiEditable` come from the backend per-row (locked
// checkboxes stay disabled even if this config drifts).
const NOTIFICATION_CATALOG = [
    {
        key: 'BOOKING_CONFIRMED',
        label: 'Booking Confirmed',
        description: "Sent when a student's payment is verified and their seat is booked.",
        previewStudent: '"✅ Booking Confirmed! Hi {name}, Plan: {plan}, Seat: {seat}…"',
        previewAdmin: '"📚 New Booking! Student: {name}, Seat: {seat}…"',
    },
    {
        key: 'STUDENT_ID_CARD',
        label: 'Student ID Card',
        description: "The ID card sent right after booking confirmation. Hindi only affects the emailed copy — the WhatsApp image uses a fixed, pre-approved Meta template.",
        previewStudent: '"Dear {name}, please find your library ID card attached…"',
        previewAdmin: '"New ID card generated for {name}"',
    },
    {
        key: 'USER_REGISTERED',
        label: 'Welcome / New Registration',
        description: 'Sent right after a student creates their account.',
        previewStudent: '"🎉 Welcome to Target Zone Library! Hi {name}, your account has been created…"',
        previewAdmin: '"🆕 New Student Registered! Name: {name}, Mobile: {mobile}…"',
    },
    {
        key: 'RENEWAL_REMINDER',
        label: 'Renewal Reminder',
        description: 'Sent 7 and 3 days before a membership expires, or manually from Admin → Reminders.',
        previewStudent: '"⏰ Reminder — Membership Expiring! Hi {name}, your membership expires on {date}…"',
    },
    {
        key: 'PENDING_FEE_REMINDER',
        label: 'Pending Fee Reminder',
        description: 'Manual admin reminder for a student with an outstanding cash balance.',
        previewStudent: '"💰 Pending Fee Reminder. Hi {name}, you have a pending library fee of {amount}…"',
    },
    {
        key: 'GRACE_DUES_REMINDER',
        label: 'Grace Dues Reminder',
        description: 'Manual admin reminder for a student in grace period with unpaid dues.',
        previewStudent: '"⏳ Grace Period — Dues Reminder. Hi {name}, your membership is in its grace period…"',
    },
    {
        key: 'MEMBERSHIP_GRACE',
        label: 'Membership Entered Grace Period',
        description: 'Fired automatically the moment a membership lapses into grace — a student alert and a "seat held" admin alert.',
        previewStudent: '"⚠️ Your Membership Has Expired. Hi {name}, we\'re holding your seat…"',
        previewAdmin: '"🔒 Seat Held — Grace Period Started. Seat: {seat}, Student: {name}…"',
    },
    {
        key: 'PENDING_FEE_CLEARED',
        label: 'Pending Fee Cleared',
        description: 'Confirmation sent to the student and an audit alert to admin after a cash balance is cleared.',
        previewStudent: '"✅ Pending Fee Cleared. Hi {name}, your pending library fee has been cleared…"',
        previewAdmin: '"✅ Pending Fee Cleared. Student: {name}, Amount: {amount}"',
    },
    {
        key: 'PAYMENT_RECEIPT',
        label: 'Payment Receipt',
        description: 'The receipt sent after any confirmed payment. Hindi only affects the emailed copy — WhatsApp uses a fixed, pre-approved document template.',
        previewStudent: '"Dear {name}, please find attached your payment receipt…"',
        previewAdmin: '"Dear {name}, please find attached your payment receipt…" (sent to admin)',
    },
    {
        key: 'ADMIN_BROADCAST',
        label: 'Broadcast Messages',
        description: "The ad-hoc message an admin sends to active members from Admin → Broadcast. Admin always gets a send confirmation, and each broadcast's text is written fresh, so there's no Hindi option here.",
        isBroadcast: true,
    },
]

function NotificationSettingCard({ catalog, row, onSaved }) {
    const [sendToStudent, setSendToStudent]         = useState(row.sendToStudent)
    const [sendToAdmin, setSendToAdmin]             = useState(row.sendToAdmin)
    const [hindiEnabled, setHindiEnabled]           = useState(row.hindiEnabled)
    const [hindiTextStudent, setHindiTextStudent]   = useState(row.hindiTextStudent || '')
    const [hindiTextAdmin, setHindiTextAdmin]       = useState(row.hindiTextAdmin || '')
    const [saving, setSaving]                       = useState(false)

    const save = async () => {
        setSaving(true)
        try {
            const res = await api.patch(`/admin/notification-settings/${catalog.key}`, {
                sendToStudent, sendToAdmin, hindiEnabled, hindiTextStudent, hindiTextAdmin,
            })
            onSaved(res.data.data)
            toast.success(`${catalog.label} settings saved`)
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to save')
        } finally {
            setSaving(false)
        }
    }

    return (
        <div className="card p-5 mb-4">
            <p className="text-white font-semibold">{catalog.label}</p>
            <p className="text-primary-400 text-xs mt-1 mb-3">{catalog.description}</p>

            <div className="flex flex-wrap gap-4 mb-3">
                {catalog.isBroadcast ? (
                    <label className="flex items-center gap-2 text-sm text-primary-200">
                        <input type="checkbox" className="rounded" checked={sendToStudent}
                               onChange={e => setSendToStudent(e.target.checked)} />
                        Enable Broadcast Feature
                    </label>
                ) : (
                    <>
                        <label className={`flex items-center gap-2 text-sm ${row.studentEditable ? 'text-primary-200' : 'text-primary-500'}`}>
                            <input type="checkbox" className="rounded" checked={sendToStudent}
                                   disabled={!row.studentEditable}
                                   onChange={e => setSendToStudent(e.target.checked)} />
                            Send to Student
                        </label>
                        <label className={`flex items-center gap-2 text-sm ${row.adminEditable ? 'text-primary-200' : 'text-primary-500'}`}>
                            <input type="checkbox" className="rounded" checked={sendToAdmin}
                                   disabled={!row.adminEditable}
                                   onChange={e => setSendToAdmin(e.target.checked)} />
                            Send to Admin
                            {!row.adminEditable && <span className="text-primary-600 text-xs">(not available)</span>}
                        </label>
                    </>
                )}
            </div>

            {row.hindiEditable && (
                <div className="border-t border-primary-700/30 pt-3 mt-1">
                    <label className="flex items-center gap-2 text-sm text-primary-200 mb-2">
                        <input type="checkbox" className="rounded" checked={hindiEnabled}
                               onChange={e => setHindiEnabled(e.target.checked)} />
                        Include Hindi translation in the same message
                    </label>
                    {hindiEnabled && (
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            {sendToStudent && (
                                <div>
                                    <label className="label text-xs">Student message — Hindi</label>
                                    <p className="text-primary-500 text-xs mb-1 italic">{catalog.previewStudent}</p>
                                    <textarea className="input w-full text-sm" rows={3}
                                              value={hindiTextStudent}
                                              onChange={e => setHindiTextStudent(e.target.value)} />
                                </div>
                            )}
                            {sendToAdmin && catalog.previewAdmin && (
                                <div>
                                    <label className="label text-xs">Admin message — Hindi</label>
                                    <p className="text-primary-500 text-xs mb-1 italic">{catalog.previewAdmin}</p>
                                    <textarea className="input w-full text-sm" rows={3}
                                              value={hindiTextAdmin}
                                              onChange={e => setHindiTextAdmin(e.target.value)} />
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}

            <button onClick={save} disabled={saving}
                    className="btn-ghost text-xs border border-primary-700/40 px-3 py-1.5 rounded-lg mt-3">
                {saving ? 'Saving…' : 'Save'}
            </button>
        </div>
    )
}

function NotificationSettingsSection() {
    const [rows, setRows]       = useState([])
    const [loading, setLoading] = useState(true)

    const load = async () => {
        setLoading(true)
        try {
            const res = await api.get('/admin/notification-settings')
            setRows(res.data.data || [])
        } catch {
            toast.error('Failed to load notification settings')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => { load() }, [])

    const updateRow = (key, patched) => {
        setRows(prev => prev.map(r => r.notificationKey === key ? patched : r))
    }

    return (
        <div className="mb-6">
            <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">Notification Settings</p>
            <p className="text-primary-500 text-xs mb-4">
                Turn notifications on/off, choose who receives them, and optionally add a Hindi
                translation that's appended to the same WhatsApp/email message.
            </p>
            {loading ? (
                <div className="space-y-3">{[1, 2, 3].map(i => <div key={i} className="shimmer h-24 rounded-xl" />)}</div>
            ) : (
                NOTIFICATION_CATALOG.map(cat => {
                    const row = rows.find(r => r.notificationKey === cat.key)
                    if (!row) return null
                    return (
                        <NotificationSettingCard
                            key={cat.key}
                            catalog={cat}
                            row={row}
                            onSaved={patched => updateRow(cat.key, patched)}
                        />
                    )
                })
            )}
        </div>
    )
}

export default function AdminSettingsPage() {
    const { t } = useTranslation()

    const [loading, setLoading] = useState(true)
    const [saving,  setSaving]  = useState(false)

    const [wifiName,        setWifiName]        = useState('')
    const [wifiPassword,    setWifiPassword]    = useState('')
    const [showPassword,    setShowPassword]    = useState(false)
    const [graceDays,       setGraceDays]       = useState('')
    const [convenienceFee,  setConvenienceFee]  = useState('')
    const [waterTankerRate, setWaterTankerRate] = useState('')

    const num = v => parseFloat(v) || 0

    const load = async () => {
        setLoading(true)
        try {
            const res = await api.get('/admin/settings')
            const d = res.data.data ?? {}
            setWifiName(d.wifiName ?? '')
            setWifiPassword(d.wifiPassword ?? '')
            setGraceDays(d.graceDays ?? 10)
            setConvenienceFee(d.convenienceFee ?? 0)
            setWaterTankerRate(d.waterTankerRate ?? 0)
        } catch {
            toast.error(t('adminSettings.loadFailed'))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => { load() }, [])

    const save = async () => {
        setSaving(true)
        try {
            await api.post('/admin/settings', {
                wifiName,
                wifiPassword,
                graceDays: parseInt(graceDays || '0', 10),
                convenienceFee: num(convenienceFee),
                waterTankerRate: num(waterTankerRate),
            })
            toast.success(t('adminSettings.saved'))
        } catch (e) {
            toast.error(e.response?.data?.message || t('adminSettings.saveFailed'))
        } finally {
            setSaving(false)
        }
    }

    return (
        <div className="max-w-2xl mx-auto">
            <div className="mb-6">
                <h1 className="page-header">{t('adminSettings.title')}</h1>
                <p className="text-primary-400">{t('adminSettings.subtitle')}</p>
            </div>

            {loading ? (
                <div className="space-y-3">
                    {[1, 2, 3].map(i => <div key={i} className="shimmer h-24 rounded-xl" />)}
                </div>
            ) : (
                <>
                    {/* WiFi */}
                    <div className="card p-5 mb-4">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminSettings.wifiSection')}</p>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <div>
                                <label className="label">{t('adminSettings.wifiName')}</label>
                                <input
                                    type="text"
                                    className="input w-full"
                                    value={wifiName}
                                    onChange={e => setWifiName(e.target.value)}
                                />
                            </div>
                            <div>
                                <label className="label">{t('adminSettings.wifiPassword')}</label>
                                <div className="flex items-center gap-2">
                                    <input
                                        type={showPassword ? 'text' : 'password'}
                                        className="input w-full"
                                        value={wifiPassword}
                                        onChange={e => setWifiPassword(e.target.value)}
                                    />
                                    <button
                                        type="button"
                                        onClick={() => setShowPassword(s => !s)}
                                        className="text-xs px-2 py-2 rounded-lg bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all flex-shrink-0"
                                    >
                                        {showPassword ? '🙈' : '👁'}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Grace period */}
                    <div className="card p-5 mb-4">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminSettings.gracePeriodSection')}</p>
                        <SettingField label={t('adminSettings.graceDays')} value={graceDays} onChange={setGraceDays} integer />
                    </div>

                    {/* Convenience fee */}
                    <div className="card p-5 mb-4">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminSettings.convenienceFeeSection')}</p>
                        <SettingField label={t('adminSettings.convenienceFee')} value={convenienceFee} onChange={setConvenienceFee} prefix="₹" />
                    </div>

                    {/* Water tanker rate */}
                    <div className="card p-5 mb-6">
                        <p className="text-primary-400 text-xs uppercase tracking-widest mb-3">{t('adminSettings.waterTankerSection')}</p>
                        <SettingField label={t('adminSettings.waterTankerRate')} value={waterTankerRate} onChange={setWaterTankerRate} prefix="₹" />
                    </div>

                    <button
                        onClick={save}
                        disabled={saving}
                        className="btn-primary w-full py-3 text-base"
                    >
                        {saving ? t('adminSettings.saving') : t('adminSettings.save')}
                    </button>

                    {/* Notification settings — saves per-row, independent of the button above */}
                    <div className="mt-8">
                        <NotificationSettingsSection />
                    </div>
                </>
            )}
        </div>
    )
}
