import { useNavigate } from 'react-router-dom'

export default function NotFoundPage() {
    const navigate = useNavigate()

    return (
        <div className="min-h-screen bg-primary-950 flex items-center justify-center p-6">
            <div className="text-center max-w-md">
                <p className="text-8xl font-bold text-amber-400 font-display mb-2">404</p>
                <h1 className="text-2xl font-bold text-white mb-3">Page Not Found</h1>
                <p className="text-primary-400 mb-8">
                    The page you're looking for doesn't exist or has been moved.
                </p>
                <div className="flex flex-col sm:flex-row gap-3 justify-center">
                    <button onClick={() => navigate(-1)} className="btn-outline px-6 py-2.5">
                        ← Go Back
                    </button>
                    <button onClick={() => navigate('/')} className="btn-primary px-6 py-2.5">
                        Go Home
                    </button>
                </div>
            </div>
        </div>
    )
}
