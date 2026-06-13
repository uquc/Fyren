const ws = new WebSocket('ws://127.0.0.1:9222/devtools/page/6EBCFB8408A71A6E9F727962E43C6652');
let msgId = 0;
function send(method, params) {
    ws.send(JSON.stringify({id: ++msgId, method, params}));
}

ws.onopen = () => {
    send('Runtime.enable');
    send('Console.enable');
    
    setTimeout(() => {
        // Fetch the assets.txt content
        send('Runtime.evaluate', {
            expression: 'fetch("/assets/assets.txt").then(r => r.text()).then(t => t.length + " bytes: [" + t + "]").catch(e => "error: " + e)',
            returnByValue: true,
            awaitPromise: true
        });
        send('Runtime.evaluate', {
            expression: 'fetch("/fyren/assets.txt").then(r => r.status + ":" + r.statusText).catch(e => "error: " + e)',
            returnByValue: true,
            awaitPromise: true
        });
        // Check the preloader state
        send('Runtime.evaluate', {
            expression: 'var table = document.querySelector("table"); table ? table.outerHTML.substring(0, 500) : "no table"',
            returnByValue: true
        });
    }, 3000);
};

ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    if (msg.result?.result?.value !== undefined) console.log('>' + msg.id, JSON.stringify(msg.result.result.value).substring(0, 600));
};

setTimeout(() => { ws.close(); process.exit(0); }, 8000);
