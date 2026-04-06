import React, { useMemo, useRef, useState } from "react";
import "../styles/global.css";

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
  const [dragActive, setDragActive] = useState(false);
  const [logs, setLogs] = useState([]);

  const fileInputRef = useRef(null);

  const [form, setForm] = useState({
    username: "",
    password: "",
    clientId: "",
    clientSecret: "",
    tokenUrl: "",
    grantType: "client_credentials",
  });

  const canRun = useMemo(() => !!file && !busy, [file, busy]);

  const handleSelectedFile = (selected) => {
    setStatus("");
    setReportUrl("");

    if (!selected) {
      setFile(null);
      return;
    }

    const lower = selected.name.toLowerCase();
    if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
      setStatus("Only .xlsx or .xls files are allowed.");
      setFile(null);
      return;
    }

    setFile(selected);
    setStatus(`Selected file: ${selected.name}`);
  };

  const onFileChange = (event) => {
    const selected = event.target.files?.[0];
    handleSelectedFile(selected);
  };

  const handleDrag = (event) => {
    event.preventDefault();
    event.stopPropagation();

    if (event.type === "dragenter" || event.type === "dragover") {
      setDragActive(true);
    } else if (event.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = (event) => {
    event.preventDefault();
    event.stopPropagation();
    setDragActive(false);

    const droppedFile = event.dataTransfer.files?.[0];
    handleSelectedFile(droppedFile);
  };

  const updateField = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const pollLogs = () => {
    const interval = setInterval(async () => {
      try {
        const response = await fetch("http://localhost:8080/logs");
        if (!response.ok) {
          clearInterval(interval);
          return;
        }

        const data = await response.json();
        setLogs(data);

        const joined = data.join(" | ");

        if (
          joined.includes("Execution of the Test cases has been completed..") ||
          joined.includes("Execution Stop time:")
        ) {
          clearInterval(interval);
        }
      } catch (error) {
        clearInterval(interval);
      }
    }, 1000);

    return interval;
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

    let logInterval;

    try {
      setBusy(true);
      setStatus("Running tests...");
      logInterval = pollLogs();

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
      <div className="card">
        <h1> API Testing Framework CPI</h1>
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
          <div
            className={`dropzone ${dragActive ? "active" : ""}`}
            onDragEnter={handleDrag}
            onDragOver={handleDrag}
            onDragLeave={handleDrag}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            style={{ cursor: "pointer" }}
          >
            <p>
              {file
                ? `Selected file: ${file.name}`
                : "Drag & drop your Excel file here, or click to browse"}
            </p>

            <input
              ref={fileInputRef}
              type="file"
              accept=".xlsx,.xls"
              style={{ display: "none" }}
              onChange={onFileChange}
            />
          </div>
          <small>Expected sheet columns: {REQUIRED_COLUMNS.join(", ")}</small>
        </div>

        <button className="runBtn" disabled={!canRun} onClick={runTests}>
          {busy ? "Running..." : "Run Tests"}
        </button>

        {status ? <div className="status">{status}</div> : null}

        {logs.length > 0 && (
          <div className="status logsBox">
            <strong>Execution Logs</strong>
            <div className="logsList">
              {logs.map((log, index) => (
                <div key={index} className="logLine">
                  • {log}
                </div>
              ))}
            </div>
          </div>
        )}

        {reportUrl ? (
          <div className="section">
            <a href={reportUrl} target="_blank" rel="noreferrer">
              Download generated report
            </a>
          </div>
        ) : null}
      </div>
   
  );
}
