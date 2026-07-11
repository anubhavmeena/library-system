import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import QRCode from 'qrcode'
import { useTranslation } from 'react-i18next'
import { buildUpiDeepLink } from '../../utils/upiPay'

export default function UpiQrModal({ vpa, amount, studentName, onClose }) {
    const { t } = useTranslation()
    const [dataUrl, setDataUrl] = useState(null)

    useEffect(() => {
        const link = buildUpiDeepLink({
            vpa, payeeName: 'Target Zone Library', amount,
            note: `Library fee - ${studentName || ''}`,
        })
        QRCode.toDataURL(link, { width: 280, margin: 1 }).then(setDataUrl)
    }, [vpa, amount, studentName])

    return createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={onClose}>
            <div className="card p-6 max-w-sm w-full text-center" onClick={e => e.stopPropagation()}>
                <h3 className="section-title mb-1">{t('adminNewMembership.step4.qrModalTitle')}</h3>
                <p className="text-primary-400 text-sm mb-4">{t('adminNewMembership.step4.qrModalSubtitle', { amount })}</p>
                {dataUrl
                    ? <img src={dataUrl} alt="UPI QR code" className="mx-auto rounded-xl bg-white p-3" />
                    : <div className="shimmer h-64 w-64 mx-auto rounded-xl" />}
                <button onClick={onClose} className="btn-ghost border border-primary-700/40 px-6 py-2.5 rounded-xl text-sm mt-5">
                    {t('adminNewMembership.step4.close')}
                </button>
            </div>
        </div>,
        document.body
    )
}
