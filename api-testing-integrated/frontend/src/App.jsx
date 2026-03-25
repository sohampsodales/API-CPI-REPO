import React, { useMemo, useState } from "react";
import logo from "./assets/logo-sodales-dark.png";

const REQUIRED_COLUMNS = [
  "Test Name",
  "Method",
  "URL",
  "Headers",
  "Payload",
  "Expected Status",
  "Expected Response",
  "Skip",
];

export default function App() {
  const [authType, setAuthType] = useState("BASIC");
  const [file, setFile] = useState(null);
  const [status, setStatus] = useState("");
  const [busy, setBusy] = useState(false);
  const [reportUrl, setReportUrl] = useState("");
  const [showClientSecret, setShowClientSecret] = useState(false);

  const [form, setForm] = useState({
    username: "",
    password: "",
    clientId: "",
    clientSecret: "",
    tokenUrl: "",
    grantType: "client_credentials",
  });

  const canRun = useMemo(() => !!file && !busy, [file, busy]);

  const onFileChange = (event) => {
    const selected = event.target.files?.[0];
    setStatus("");
    setReportUrl("");

    if (!selected) {
      setFile(null);
      return;
    }

    const lower = selected.name.toLowerCase();
    if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
      setStatus("Only .xlsx or .xls files are allowed.");
      event.target.value = "";
      setFile(null);
      return;
    }

    setFile(selected);
  };

  const updateField = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const runTests = async () => {
    setStatus("");
    setReportUrl("");

    if (!file) {
      setStatus("Please choose an Excel file.");
      return;
    }

    if (authType === "BASIC" && (!form.username || !form.password)) {
      setStatus("Username and password are required for Basic authentication.");
      return;
    }

    if (
      authType === "OAUTH" &&
      (!form.clientId || !form.clientSecret || !form.tokenUrl)
    ) {
      setStatus(
        "Token URL, Client ID and Client Secret are required for OAuth.",
      );
      return;
    }

    const data = new FormData();
    data.append("file", file);

    // Keep UI label OAUTH2, backend can map this to OAUTH if needed
    data.append("authType", authType);

    data.append("username", form.username);
    data.append("password", form.password);
    data.append("clientId", form.clientId);
    data.append("clientSecret", form.clientSecret);
    data.append("tokenUrl", form.tokenUrl);
    data.append("grantType", form.grantType || "client_credentials");

    try {
      setBusy(true);
      setStatus("Running tests...");

      const response = await fetch("http://localhost:8080/run-tests", {
        method: "POST",
        body: data,
      });

      if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Execution failed");
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);

      const a = document.createElement("a");
      a.href = url;
      a.download = "api-test-results.zip";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);

      setReportUrl(url);
      setStatus("Execution completed. Report downloaded.");
    } catch (error) {
      setStatus(error.message || "Execution failed.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="card">
        <div className="header">
          <img src={logo} alt="Logo" className="logo" />
        </div>
        <h1> API Testing Framework</h1>
        <p className="subtitle">
          Choose Authentication & Upload your Excel File
        </p>

        <div className="section">
          <label>Authentication Type</label>
          <select
            value={authType}
            onChange={(e) => setAuthType(e.target.value)}
          >
            <option value="BASIC">Basic</option>
            <option value="OAUTH">OAuth</option>
          </select>
        </div>

        {authType === "BASIC" ? (
          <div className="grid two">
            <div>
              <label>Username</label>
              <input
                value={form.username}
                onChange={(e) => updateField("username", e.target.value)}
                placeholder="Enter username"
              />
            </div>
            <div>
              <label>Password</label>
              <input
                type="password"
                value={form.password}
                onChange={(e) => updateField("password", e.target.value)}
                placeholder="Enter password"
              />
            </div>
          </div>
        ) : (
          <div className="grid two">
            <div>
              <label>Token URL</label>
              <input
                value={form.tokenUrl}
                onChange={(e) => updateField("tokenUrl", e.target.value)}
                placeholder="https://.../token"
              />
            </div>
            <div>
              <label>Grant Type</label>
              <input
                value={form.grantType}
                onChange={(e) => updateField("grantType", e.target.value)}
                placeholder="client_credentials"
                disabled
              />
            </div>
            <div>
              <label>Client ID</label>
              <input
                value={form.clientId}
                onChange={(e) => updateField("clientId", e.target.value)}
                placeholder="Enter client id"
              />
            </div>
            <div className="input-wrapper">
              <label>Client Secret</label>

              <div className="input-field">
                <input
                  type={showClientSecret ? "text" : "password"}
                  value={form.clientSecret}
                  onChange={(e) => updateField("clientSecret", e.target.value)}
                  placeholder="Enter client secret"
                />

                <button
                  type="button"
                  className="toggle-btn"
                  onClick={() => setShowClientSecret((prev) => !prev)}
                >
                  {showClientSecret ? "Hide" : "Show"}
                </button>
              </div>
            </div>
          </div>
        )}

        <div className="section">
          <label>Excel File</label>
          <input type="file" accept=".xlsx,.xls" onChange={onFileChange} />
          <small>Expected sheet columns: {REQUIRED_COLUMNS.join(", ")}</small>
        </div>

        <button className="runBtn" disabled={!canRun} onClick={runTests}>
          {busy ? "Running..." : "Run Tests"}
        </button>

        {status ? <div className="status">{status}</div> : null}

        {reportUrl ? (
          <div className="section">
            <a href={reportUrl} target="_blank" rel="noreferrer">
              Download generated report
            </a>
          </div>
        ) : null}
      </div>
    </div>
  );
}
