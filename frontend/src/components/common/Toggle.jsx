// Pill switch — no native <input type="checkbox"> equivalent exists elsewhere
// in this codebase (settings/notification toggles all use plain checkboxes),
// but a global on/off kill switch reads better as a switch than a checkbox.
export default function Toggle({ checked, onChange, disabled = false, label }) {
    return (
        <button
            type="button"
            role="switch"
            aria-checked={checked}
            disabled={disabled}
            onClick={() => onChange(!checked)}
            className={`relative inline-flex h-6 w-11 flex-shrink-0 items-center rounded-full border transition-colors duration-200 ${
                checked ? 'bg-amber-500 border-amber-400' : 'bg-primary-800 border-primary-700/60'
            } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
            aria-label={label}
        >
            <span
                className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform duration-200 ${
                    checked ? 'translate-x-6' : 'translate-x-1'
                }`}
            />
        </button>
    )
}
