function build(r) {
    const body = JSON.stringify({
        error: {
            code: "rate_limited",
            message: "Too many requests",
            request_id: r.variables.pullhire_request_id,
            timestamp: new Date().toISOString()
        }
    });
    r.headersOut["Content-Type"] = "application/problem+json; charset=utf-8";
    r.return(429, body);
}
export default { build };
