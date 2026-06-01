// Cloudflare Pages SPA Function
// Routes all non-asset requests to index.html for client-side routing
export async function onRequest(context) {
  const url = new URL(context.request.url)
  const pathname = url.pathname

  // Static assets - serve directly
  if (
    pathname.startsWith('/assets/') ||
    pathname.startsWith('/images/') ||
    pathname === '/favicon.ico' ||
    pathname === '/_redirects' ||
    pathname === '/_routes.json'
  ) {
    return context.env.ASSETS.fetch(context.request)
  }

  // All other routes → serve index.html (SPA)
  const indexUrl = new URL('/index.html', url.origin)
  const response = await context.env.ASSETS.fetch(indexUrl)
  return new Response(response.body, {
    status: 200,
    statusText: 'OK',
    headers: response.headers
  })
}
