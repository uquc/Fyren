const ws = new WebSocket('ws://127.0.0.1:9222/devtools/page/6EBCFB8408A71A6E9F727962E43C6652');
let msgId = 0;
function send(method, params) {
    ws.send(JSON.stringify({id: ++msgId, method, params}));
}

ws.onopen = () => {
    send('Runtime.enable');
    
    setTimeout(() => {
        // Bypass the preloader by checking what functions exist
        send('Runtime.evaluate', {
            expression: 'Object.keys(window).filter(k => k.startsWith("com_") || k.includes("fyren") || k.includes("Fyren")).slice(0, 20).join(", ")',
            returnByValue: true
        });
        // Check if any GWT compiled functions are available
        send('Runtime.evaluate', {
            expression: 'typeof window.fyren',
            returnByValue: true
        });
    }, 5000);
};

ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    if (msg.result?.result?.value !== undefined) console.log('>' + msg.id, JSON.stringify(msg.result.result.value).substring(0, 500));
};

setTimeout(() => { ws.close(); process.exit(0); }, 10000);
