const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const {URL} = require('url');

const HOST = process.env.HOST || '127.0.0.1';
const PORT = Number(process.env.PORT || 3000);
const GATEWAY_URL = process.env.ATOM_GATEWAY_URL || 'http://localhost:8081';
const STATIC_ROOT = __dirname;
const gateway = new URL(GATEWAY_URL);

const contentTypes = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.ico': 'image/x-icon'
};

const server = http.createServer((req, res) => {
    if ((req.url || '').startsWith('/api/')) {
        proxyToGateway(req, res);
        return;
    }

    serveStatic(req, res);
});

server.listen(PORT, HOST, () => {
    console.log(`A.T.O.M. test UI is up at http://${HOST}:${PORT}`);
    console.log(`Proxy target: ${GATEWAY_URL}`);
});

function proxyToGateway(clientReq, clientRes) {
    const transport = gateway.protocol === 'https:' ? https : http;
    const proxyReq = transport.request({
        protocol: gateway.protocol,
        hostname: gateway.hostname,
        port: gateway.port || (gateway.protocol === 'https:' ? 443 : 80),
        method: clientReq.method,
        path: clientReq.url,
        headers: {
            ...clientReq.headers,
            host: gateway.host,
            origin: GATEWAY_URL
        }
    }, (proxyRes) => {
        clientRes.writeHead(proxyRes.statusCode || 502, proxyRes.headers);
        proxyRes.pipe(clientRes, {end: true});
    });

    proxyReq.on('error', (error) => {
        const payload = JSON.stringify({
            status: 502,
            error: 'Proxy error',
            message: error.message,
            target: GATEWAY_URL
        });
        clientRes.writeHead(502, {'Content-Type': 'application/json; charset=utf-8'});
        clientRes.end(payload);
    });

    clientReq.pipe(proxyReq, {end: true});
}

function serveStatic(req, res) {
    const requestUrl = new URL(req.url || '/', `http://${req.headers.host || `${HOST}:${PORT}`}`);
    let relativePath = requestUrl.pathname === '/' ? '/index.html' : requestUrl.pathname;
    relativePath = path.normalize(relativePath).replace(/^(\.\.[/\\])+/, '');

    const filePath = path.join(STATIC_ROOT, relativePath);

    if (!filePath.startsWith(STATIC_ROOT)) {
        res.writeHead(403, {'Content-Type': 'text/plain; charset=utf-8'});
        res.end('Forbidden');
        return;
    }

    fs.readFile(filePath, (error, fileBuffer) => {
        if (error) {
            if (error.code === 'ENOENT') {
                res.writeHead(404, {'Content-Type': 'text/plain; charset=utf-8'});
                res.end('Not found');
                return;
            }

            res.writeHead(500, {'Content-Type': 'text/plain; charset=utf-8'});
            res.end('Static read error');
            return;
        }

        const ext = path.extname(filePath).toLowerCase();
        res.writeHead(200, {
            'Content-Type': contentTypes[ext] || 'application/octet-stream',
            'Cache-Control': 'no-store'
        });
        res.end(fileBuffer);
    });
}
