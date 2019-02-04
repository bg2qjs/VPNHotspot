package be.mygod.vpnhotspot

import be.mygod.vpnhotspot.App.Companion.app
import be.mygod.vpnhotspot.net.Routing
import be.mygod.vpnhotspot.widget.SmartSnackbar
import timber.log.Timber

abstract class RoutingManager(private val caller: Any, val downstream: String) {
    companion object {
        private const val KEY_MASQUERADE_MODE = "service.masqueradeMode"
        var masqueradeMode: Routing.MasqueradeMode
            get() {
                app.pref.getString(KEY_MASQUERADE_MODE, null)?.let { return Routing.MasqueradeMode.valueOf(it) }
                return if (app.pref.getBoolean("service.masquerade", true)) // legacy settings
                    Routing.MasqueradeMode.Simple else Routing.MasqueradeMode.None
            }
            set(value) = app.pref.edit().putString(KEY_MASQUERADE_MODE, value.name).apply()
    }

    class LocalOnly(caller: Any, downstream: String) : RoutingManager(caller, downstream) {
        override fun Routing.configure() {
            ipForward() // local only interfaces need to enable ip_forward
            forward()
            masquerade(masqueradeMode)
            commit(true)
        }
    }

    var routing: Routing? = null

    init {
        app.onPreCleanRoutings[this] = { routing?.stop() }
        app.onRoutingsCleaned[this] = { initRouting() }
    }

    fun initRouting() = try {
        routing = Routing(caller, downstream).apply {
            try {
                configure()
            } catch (e: Exception) {
                revert()
                throw e
            }
        }
        true
    } catch (e: Exception) {
        SmartSnackbar.make(e).show()
        Timber.w(e)
        routing = null
        false
    }

    protected abstract fun Routing.configure()

    fun stop() {
        app.onPreCleanRoutings -= this
        app.onRoutingsCleaned -= this
        routing?.revert()
    }
}
