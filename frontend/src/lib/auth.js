// Session token lives in sessionStorage: the JWT is kept for the tab lifetime
// only and never touches localStorage, so a parked-but-closed tab does not
// leave a bearer token behind on a shared machine.
const TOKEN_KEY = 'am.token'

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) sessionStorage.setItem(TOKEN_KEY, token)
  else sessionStorage.removeItem(TOKEN_KEY)
}
