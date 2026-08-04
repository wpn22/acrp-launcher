const loginOptionsCancelContainer = document.getElementById('loginOptionCancelContainer')
const loginOptionMicrosoft = document.getElementById('loginOptionMicrosoft')
const loginOptionMojang = document.getElementById('loginOptionMojang')
const loginOptionOffline = document.getElementById('loginOptionOffline')
const loginOptionsCancelButton = document.getElementById('loginOptionCancelButton')
const offlineLoginPanel = document.getElementById('offlineLoginPanel')
const offlineUsername = document.getElementById('offlineUsername')
const offlineLoginError = document.getElementById('offlineLoginError')
const offlineLoginCancel = document.getElementById('offlineLoginCancel')

let loginOptionsCancellable = false

let loginOptionsViewOnLoginSuccess
let loginOptionsViewOnLoginCancel
let loginOptionsViewOnCancel
let loginOptionsViewCancelHandler

function loginOptionsCancelEnabled(val){
    if(val){
        $(loginOptionsCancelContainer).show()
    } else {
        $(loginOptionsCancelContainer).hide()
    }
}

function offlineLoginPanelEnabled(val){
    offlineLoginPanel.toggleAttribute('active', val)
    offlineLoginError.style.display = 'none'
    if(val) {
        offlineUsername.focus()
    } else {
        offlineUsername.value = ''
    }
}

loginOptionMicrosoft.onclick = (e) => {
    switchView(getCurrentView(), VIEWS.waiting, 500, 500, () => {
        ipcRenderer.send(
            MSFT_OPCODE.OPEN_LOGIN,
            loginOptionsViewOnLoginSuccess,
            loginOptionsViewOnLoginCancel
        )
    })
}

loginOptionMojang.onclick = (e) => {
    switchView(getCurrentView(), VIEWS.login, 500, 500, () => {
        loginViewOnSuccess = loginOptionsViewOnLoginSuccess
        loginViewOnCancel = loginOptionsViewOnLoginCancel
        loginCancelEnabled(true)
    })
}

loginOptionOffline.onclick = () => {
    offlineLoginPanelEnabled(true)
}

offlineLoginCancel.onclick = () => {
    offlineLoginPanelEnabled(false)
}

offlineLoginPanel.onsubmit = async e => {
    e.preventDefault()
    const displayName = offlineUsername.value.trim()
    if(!/^[A-Za-z0-9_]{3,16}$/.test(displayName)) {
        offlineLoginError.style.display = 'block'
        return
    }

    const value = AuthManager.addOfflineAccount(displayName)
    updateSelectedAccount(value)
    offlineLoginPanelEnabled(false)
    switchView(getCurrentView(), loginOptionsViewOnLoginSuccess, 500, 500, async () => {
        if(loginOptionsViewOnLoginSuccess === VIEWS.settings) {
            await prepareSettings()
        }
    })
}

loginOptionsCancelButton.onclick = (e) => {
    switchView(getCurrentView(), loginOptionsViewOnCancel, 500, 500, () => {
        // Clear login values (Mojang login)
        // No cleanup needed for Microsoft.
        loginUsername.value = ''
        loginPassword.value = ''
        offlineLoginPanelEnabled(false)
        if(loginOptionsViewCancelHandler != null){
            loginOptionsViewCancelHandler()
            loginOptionsViewCancelHandler = null
        }
    })
}
