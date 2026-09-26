
import { useEffect, useState } from "react";
import satqueryLogo from "./assets/logo.png";
import "./App.css";

const MODELS = [
  {
    name: "InternVL3 + LoRA",
    description:
      "Single-image visual question answering and image understanding.",
  },
  {
    name: "GeoGround",
    description: "Visual grounding and spatial localization.",
  },
  {
    name: "ChangeFormerV6",
    description: "Bi-temporal change detection.",
  },
  {
    name: "CROMA + UPerNet",
    description: "Optical + SAR multimodal analysis.",
  },
];

const BACKEND_URL = "http://localhost:8080";

function App() {
  const [analysisMode, setAnalysisMode] = useState("auto");
  const [selectedModel, setSelectedModel] = useState(MODELS[0].name);

  const [imageOne, setImageOne] = useState(null);
  const [imageTwo, setImageTwo] = useState(null);

  // Browser-safe preview URLs for the uploaded imagery.
  const [imageOneUrl, setImageOneUrl] = useState("");
  const [imageTwoUrl, setImageTwoUrl] = useState("");

  // Position of the interactive before/after comparison divider.
  const [comparePosition, setComparePosition] = useState(50);

  const [query, setQuery] = useState("");

  const [analyzing, setAnalyzing] = useState(false);
  const [analysisComplete, setAnalysisComplete] = useState(false);

  const [confidence, setConfidence] = useState(null);
  const [analysisResult, setAnalysisResult] = useState(null);
  const [error, setError] = useState("");

  // ================= REPORT STATE =================
  const [reportGenerated, setReportGenerated] = useState(false);
  const [reportPreviewOpen, setReportPreviewOpen] = useState(false);

  const currentModel =
    MODELS.find((model) => model.name === selectedModel) || MODELS[0];

  useEffect(() => {
    if (!imageOne) {
      setImageOneUrl("");
      return;
    }

    const url = URL.createObjectURL(imageOne);
    setImageOneUrl(url);

    return () => URL.revokeObjectURL(url);
  }, [imageOne]);

  useEffect(() => {
    if (!imageTwo) {
      setImageTwoUrl("");
      return;
    }

    const url = URL.createObjectURL(imageTwo);
    setImageTwoUrl(url);

    return () => URL.revokeObjectURL(url);
  }, [imageTwo]);

  const handleImageUpload = (event, imageNumber) => {
    const file = event.target.files?.[0];

    if (!file) return;

    if (imageNumber === 1) {
      setImageOne(file);
    } else {
      setImageTwo(file);
    }

    setAnalysisComplete(false);
    setAnalysisResult(null);
    setConfidence(null);
    setComparePosition(50);
    setError("");
    setReportGenerated(false);
    setReportPreviewOpen(false);
  };

  const handleAnalyze = async () => {
    if (!imageOne) {
      setError("Please upload Image 1 first.");
      return;
    }

    if (!query.trim()) {
      setError("Please enter a question.");
      return;
    }

    setAnalyzing(true);
    setAnalysisComplete(false);
    setConfidence(null);
    setAnalysisResult(null);
    setError("");
    setReportGenerated(false);
    setReportPreviewOpen(false);

    try {
      // ==========================================
      // STEP 1: Upload Image 1
      // ==========================================

      const image1Form = new FormData();

      image1Form.append("file", imageOne);

      const image1Response = await fetch(
        `${BACKEND_URL}/api/images/upload?projectId=1&imageType=OPTICAL`,
        {
          method: "POST",
          body: image1Form,
        }
      );

      if (!image1Response.ok) {
        const errorText = await image1Response.text();

        throw new Error(
          errorText || "Failed to upload Image 1."
        );
      }

      const image1Data = await image1Response.json();

      // ==========================================
      // STEP 2: Upload Image 2 if provided
      // ==========================================

      let image2Data = null;

      if (imageTwo) {
        const image2Form = new FormData();

        image2Form.append("file", imageTwo);

        const image2Response = await fetch(
          `${BACKEND_URL}/api/images/upload?projectId=1&imageType=OPTICAL`,
          {
            method: "POST",
            body: image2Form,
          }
        );

        if (!image2Response.ok) {
          const errorText = await image2Response.text();

          throw new Error(
            errorText || "Failed to upload Image 2."
          );
        }

        image2Data = await image2Response.json();
      }

      // ==========================================
      // STEP 3: Determine analysis type
      // ==========================================

      let analysisType = "VQA";

      const lowerQuery = query.toLowerCase();

      if (imageTwo) {
        analysisType = "CHANGE_DETECTION";
      } else if (
        lowerQuery.includes("describe") ||
        lowerQuery.includes("caption")
      ) {
        analysisType = "CAPTION";
      } else if (
        lowerQuery.includes("find") ||
        lowerQuery.includes("locate") ||
        lowerQuery.includes("where")
      ) {
        analysisType = "GROUNDING";
      }

      // ==========================================
      // STEP 4: Create analysis
      // ==========================================

      const imageIds = image2Data
        ? [image1Data.id, image2Data.id]
        : [image1Data.id];

      const analysisBody = {
        projectId: 1,
        imageIds,
        analysisType,
        question: query,
      };

      const analysisResponse = await fetch(
        `${BACKEND_URL}/api/analysis`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(analysisBody),
        }
      );

      if (!analysisResponse.ok) {
        const errorText = await analysisResponse.text();

        throw new Error(
          errorText || "Analysis request failed."
        );
      }

      // ==========================================
      // STEP 5: Receive real backend result
      // ==========================================

      const result = await analysisResponse.json();

      setAnalysisResult(result);

      setConfidence(
        result.confidence != null
          ? Math.round(result.confidence * 100)
          : null
      );

      setAnalysisComplete(true);
    } catch (err) {
      console.error("SatQuery analysis error:", err);

      setError(
        err.message ||
          "Something went wrong while analyzing the imagery."
      );
    } finally {
      setAnalyzing(false);
    }
  };

  // ================= REPORT GENERATION =================

  const getModelName = () => {
    if (typeof analysisResult?.model === "string") {
      return analysisResult.model;
    }

    return (
      analysisResult?.model?.name ||
      currentModel?.name ||
      "Gemini 3.5 Flash Lite"
    );
  };

  const getTaskName = () => {
    return (
      analysisResult?.analysisType ||
      analysisResult?.requestedAnalysisType ||
      analysisResult?.task ||
      "GENERAL_ANALYSIS"
    );
  };

  const getReportDate = () => {
    const date = analysisResult?.completedAt
      ? new Date(analysisResult.completedAt)
      : new Date();

    return date.toLocaleString();
  };

  const generateReport = () => {
    if (!analysisComplete || !analysisResult) {
      setError("Run an analysis before generating a report.");
      document
        .getElementById("analyze")
        ?.scrollIntoView({ behavior: "smooth" });
      return;
    }

    setError("");
    setReportGenerated(true);
    setReportPreviewOpen(true);
  };

  const buildReportHtml = () => {
    const evidence = Array.isArray(analysisResult?.evidence)
      ? analysisResult.evidence
      : [];

    const findings = Array.isArray(analysisResult?.findings)
      ? analysisResult.findings
      : [];

    const confidenceValue =
      analysisResult?.confidence != null
        ? `${Math.round(analysisResult.confidence * 100)}%`
        : confidence !== null
          ? `${confidence}%`
          : "Not available";

    const evidenceHtml =
      evidence.length > 0
        ? evidence
            .map(
              (item, index) => `
                <div class="evidence">
                  <div class="evidence-title">
                    Evidence ${index + 1}
                  </div>
                  <div>${escapeHtml(
                    item.description ||
                      item.label ||
                      "Visual evidence identified"
                  )}</div>
                  <div class="muted">
                    Region: x=${item.x ?? "—"}, y=${item.y ?? "—"},
                    width=${item.width ?? "—"}, height=${item.height ?? "—"}
                    ${
                      item.confidence != null
                        ? ` · confidence ${Math.round(item.confidence * 100)}%`
                        : ""
                    }
                  </div>
                </div>
              `
            )
            .join("")
        : `<p class="muted">No reliable evidence regions were identified.</p>`;

    const findingsHtml =
      findings.length > 0
        ? `<ul>${findings
            .map((finding) => `<li>${escapeHtml(String(finding))}</li>`)
            .join("")}</ul>`
        : "";

    return `
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="UTF-8" />
          <title>SatQuery AI Analysis Report</title>
          <style>
            * { box-sizing: border-box; }
            body {
              margin: 0;
              font-family: Arial, Helvetica, sans-serif;
              background: #f4f1eb;
              color: #1f2521;
              line-height: 1.6;
            }
            .page {
              max-width: 900px;
              margin: 0 auto;
              background: #ffffff;
              min-height: 100vh;
              padding: 48px 56px;
            }
            .brand {
              display: flex;
              justify-content: space-between;
              align-items: flex-start;
              border-bottom: 1px solid #d9d5cc;
              padding-bottom: 22px;
              margin-bottom: 30px;
            }
            .brand h1 {
              margin: 0;
              font-size: 28px;
              letter-spacing: -0.5px;
            }
            .brand p {
              margin: 5px 0 0;
              color: #6b706b;
            }
            .report-label {
              font-size: 11px;
              font-weight: 700;
              letter-spacing: 1.5px;
              color: #6b706b;
              text-transform: uppercase;
            }
            h2 {
              font-size: 17px;
              margin: 30px 0 12px;
            }
            .answer {
              background: #f5f4ef;
              border-left: 4px solid #526456;
              padding: 18px 20px;
              border-radius: 4px;
              font-size: 16px;
            }
            .grid {
              display: grid;
              grid-template-columns: repeat(4, 1fr);
              gap: 10px;
              margin-top: 18px;
            }
            .card {
              border: 1px solid #dedbd3;
              padding: 14px;
              border-radius: 6px;
            }
            .card span {
              display: block;
              font-size: 11px;
              color: #777b76;
              text-transform: uppercase;
              letter-spacing: .8px;
            }
            .card strong {
              display: block;
              margin-top: 5px;
              font-size: 13px;
              word-break: break-word;
            }
            .evidence {
              border: 1px solid #dedbd3;
              border-radius: 6px;
              padding: 14px 16px;
              margin-bottom: 10px;
            }
            .evidence-title {
              font-weight: 700;
              margin-bottom: 4px;
            }
            .muted {
              color: #70756f;
              font-size: 12px;
            }
            .explanation {
              border: 1px solid #dedbd3;
              padding: 16px;
              border-radius: 6px;
            }
            .footer {
              border-top: 1px solid #d9d5cc;
              margin-top: 42px;
              padding-top: 15px;
              color: #777b76;
              font-size: 11px;
            }
            @media print {
              body { background: white; }
              .page { padding: 28px; }
            }
          </style>
        </head>
        <body>
          <main class="page">
            <header class="brand">
              <div>
                <h1>SatQuery AI</h1>
                <p>Natural-language satellite intelligence</p>
              </div>
              <div class="report-label">Analysis Report</div>
            </header>

            <section>
              <div class="report-label">01 · Request</div>
              <h2>Analysis query</h2>
              <div class="answer">${escapeHtml(query)}</div>

              <div class="grid">
                <div class="card">
                  <span>Task</span>
                  <strong>${escapeHtml(getTaskName())}</strong>
                </div>
                <div class="card">
                  <span>Model</span>
                  <strong>${escapeHtml(getModelName())}</strong>
                </div>
                <div class="card">
                  <span>Input</span>
                  <strong>${imageTwo ? "Two images" : "One image"}</strong>
                </div>
                <div class="card">
                  <span>Confidence</span>
                  <strong>${confidenceValue}</strong>
                </div>
              </div>
            </section>

            <section>
              <div class="report-label">02 · AI Interpretation</div>
              <h2>Result</h2>
              <div class="answer">${escapeHtml(
                analysisResult?.answer || "No answer returned."
              )}</div>
            </section>

            ${
              analysisResult?.explanation
                ? `
                  <section>
                    <div class="report-label">03 · Explanation</div>
                    <h2>Visual reasoning summary</h2>
                    <div class="explanation">${escapeHtml(
                      analysisResult.explanation
                    )}</div>
                  </section>
                `
                : ""
            }

            <section>
              <div class="report-label">04 · Evidence</div>
              <h2>Evidence regions</h2>
              ${evidenceHtml}
            </section>

            ${
              findingsHtml
                ? `
                  <section>
                    <div class="report-label">05 · Findings</div>
                    <h2>Key findings</h2>
                    ${findingsHtml}
                  </section>
                `
                : ""
            }

            <footer class="footer">
              Generated by SatQuery AI · ${escapeHtml(getReportDate())}
              <br />
              This report summarizes AI-assisted visual interpretation and
              should not be treated as a scientifically certain measurement.
            </footer>
          </main>
        </body>
      </html>
    `;
  };

  const downloadReport = () => {
    if (!analysisComplete || !analysisResult) {
      setError("Run an analysis before downloading a report.");
      return;
    }

    const html = buildReportHtml();
    const blob = new Blob([html], { type: "text/html;charset=utf-8" });
    const url = URL.createObjectURL(blob);

    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `satquery-analysis-${Date.now()}.html`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();

    URL.revokeObjectURL(url);
    setReportGenerated(true);
  };

  const printReport = () => {
    if (!analysisComplete || !analysisResult) {
      setError("Run an analysis before printing a report.");
      return;
    }

    const reportWindow = window.open("", "_blank", "width=1000,height=800");

    if (!reportWindow) {
      setError("The report window was blocked. Allow pop-ups and try again.");
      return;
    }

    reportWindow.document.open();
    reportWindow.document.write(buildReportHtml());
    reportWindow.document.close();

    reportWindow.onload = () => {
      reportWindow.focus();
      reportWindow.print();
    };
  };

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  const setExampleQuery = (text) => {
    setQuery(text);
    setError("");
  };

  return (
    <div className="app">

      {/* ================= NAVBAR ================= */}

      <header className="navbar">

        <div className="logo">
          <div className="logo-icon">S</div>

          <div>
            <strong>SatQuery</strong>
            <span> AI</span>
          </div>
        </div>

        <nav className="navigation">

          <a href="#analyze" className="active">
            Analyze
          </a>

          <a href="#history">
            History
          </a>

          <a href="#reports">
            Reports
          </a>

        </nav>

        <button className="analyst-button">
          Analyst Mode
        </button>

      </header>


      {/* ================= HERO ================= */}

      <main>

        <section className="hero" id="analyze">

          <div className="hero-content">

            <p className="eyebrow">
              AI-POWERED SATELLITE INTELLIGENCE
            </p>

            <h1>
              Ask your satellite imagery.
              <br />

              <span>Get evidence, not guesses.</span>
            </h1>

            <p className="hero-description">
              Upload satellite imagery, ask questions in natural
              language, and let SatQuery AI route your request to
              the appropriate specialist model.
            </p>

          </div>


          <div className="router-status">

            <div className="status-indicator">
              <span></span>

              AI Router Ready
            </div>

            <p>
              Automatic model selection enabled
            </p>

          </div>

        </section>


        {/* ================= WORKSPACE ================= */}

        <section className="workspace">


          {/* ================= INPUT PANEL ================= */}

          <div className="panel input-panel">

            <div className="panel-header">

              <div>

                <p className="section-label">
                  01 · INPUT IMAGERY
                </p>

                <h2>
                  Upload satellite imagery
                </h2>

              </div>

              <div className="small-tag">
                Optical / SAR
              </div>

            </div>


            {/* IMAGE UPLOAD */}

            <div className="upload-grid">


              {/* IMAGE 1 */}

              <label className="upload-box">

                <input
                  type="file"
                  accept="image/*,.tif,.tiff"
                  onChange={(event) =>
                    handleImageUpload(event, 1)
                  }
                  hidden
                />

                <div className="upload-icon">
                  +
                </div>

                <h3>
                  Image 1
                </h3>

                <p>
                  Drag & drop or browse
                </p>

                <span className="browse-button">
                  Browse
                </span>

                {imageOne && (
                  <small className="file-name">
                    {imageOne.name}
                  </small>
                )}

              </label>


              {/* IMAGE 2 */}

              <label className="upload-box">

                <input
                  type="file"
                  accept="image/*,.tif,.tiff"
                  onChange={(event) =>
                    handleImageUpload(event, 2)
                  }
                  hidden
                />

                <div className="upload-icon">
                  +
                </div>

                <h3>
                  Image 2
                  <span className="optional">
                    optional
                  </span>
                </h3>

                <p>
                  Required for change analysis
                </p>

                <span className="browse-button">
                  Browse
                </span>

                {imageTwo && (
                  <small className="file-name">
                    {imageTwo.name}
                  </small>
                )}

              </label>

            </div>


            {/* ================= QUERY ================= */}

            <div className="query-heading">

              <img
                src={satqueryLogo}
                alt="SatQuery AI"
                className="query-logo"
              />

              <div>

                <p className="section-label">
                  02 · NATURAL-LANGUAGE QUERY
                </p>

                <label>
                  What would you like to know?
                </label>

              </div>

            </div>


            <textarea
              className="query-input"
              value={query}
              onChange={(event) =>
                setQuery(event.target.value)
              }
              placeholder="e.g. What changed between these two images?"
              rows="4"
            />


            <div className="suggestions">

              <button
                onClick={() =>
                  setExampleQuery(
                    "What changed between these two images?"
                  )
                }
              >
                Detect changes
              </button>

              <button
                onClick={() =>
                  setExampleQuery(
                    "Describe this satellite image."
                  )
                }
              >
                Describe scene
              </button>

              <button
                onClick={() =>
                  setExampleQuery(
                    "Identify buildings in this image."
                  )
                }
              >
                Find buildings
              </button>

            </div>

          </div>


          {/* ================= CONTROL PANEL ================= */}

          <div className="panel control-panel">

            <div className="panel-header">

              <div>

                <p className="section-label">
                  03 · ANALYSIS CONTROL
                </p>

                <h2>
                  Analysis configuration
                </h2>

              </div>

            </div>


            {/* AUTO / MANUAL */}

            <div className="mode-switch">

              <button
                className={
                  analysisMode === "auto"
                    ? "mode-button active"
                    : "mode-button"
                }
                onClick={() =>
                  setAnalysisMode("auto")
                }
              >
                Auto Routing
              </button>

              <button
                className={
                  analysisMode === "manual"
                    ? "mode-button active"
                    : "mode-button"
                }
                onClick={() =>
                  setAnalysisMode("manual")
                }
              >
                Manual Model
              </button>

            </div>


            {/* ================= AUTO ================= */}

            {analysisMode === "auto" && (

              <div className="auto-routing-box">

                <div className="ai-icon">
                  AI
                </div>

                <h3>Automatic model selection</h3>

                <p>
                  SatQuery AI will understand your query,
                  identify the required task, and select
                  the appropriate specialist model automatically.
                </p>

                <ul>
                  <li>Query understanding</li>
                  <li>Task identification</li>
                  <li>Specialist model routing</li>
                </ul>

              </div>

            )}


            {/* ================= MANUAL ================= */}

            {analysisMode === "manual" && (

              <div className="manual-model-box">

                <label htmlFor="model">
                  Select specialist model
                </label>

                <select
                  id="model"
                  className="model-select"
                  value={selectedModel}
                  onChange={(event) =>
                    setSelectedModel(event.target.value)
                  }
                >

                  {MODELS.map((model) => (

                    <option
                      key={model.name}
                      value={model.name}
                    >
                      {model.name}
                    </option>

                  ))}

                </select>


                <div className="selected-model-info">

                  <strong>
                    {currentModel.name}
                  </strong>

                  <p>
                    {currentModel.description}
                  </p>

                </div>

              </div>

            )}


            {/* ================= CONFIGURATION ================= */}

            <div className="configuration">

              <h4>Current configuration</h4>

              <div className="configuration-grid">

                <div className="config-item">
                  <span>Routing</span>
                  <strong>
                    {analysisMode === "auto"
                      ? "Automatic"
                      : selectedModel}
                  </strong>
                </div>

                <div className="config-item">
                  <span>Input</span>
                  <strong>
                    {imageTwo
                      ? "Dual imagery"
                      : "Single imagery"}
                  </strong>
                </div>

                <div className="config-item">
                  <span>Evidence validation</span>
                  <strong>Enabled</strong>
                </div>

                <div className="config-item">
                  <span>Comparison</span>
                  <strong>{imageTwo ? "Before / After" : "Single image"}</strong>
                </div>

              </div>
            </div>


            {/* ================= ANALYZE ================= */}

            <button
              className="analyze-button"
              onClick={handleAnalyze}
              disabled={analyzing}
            >

              {analyzing
                ? "Analyzing imagery..."
                : "Analyze imagery"}

              {!analyzing && (
                <span>
                  →
                </span>
              )}

            </button>


            {error && (
              <p className="demo-text error-text">
                {error}
              </p>
            )}

          </div>

        </section>


        {/* ================= ANALYSIS TRACE ================= */}

        <section className="panel trace-panel">

          <div className="panel-header">

            <div>

              <p className="section-label">
                04 · TRANSPARENT AI
              </p>

              <h2>
                Analysis trace
              </h2>

            </div>

            <span className="trace-label">
              Explainable pipeline
            </span>

          </div>


          <div className="trace-grid">

            <TraceStep
              number="01"
              text="Input received"
              active={true}
            />

            <TraceStep
              number="02"
              text="Query understood"
              active={analyzing || analysisComplete}
            />

            <TraceStep
              number="03"
              text="Task identified"
              active={analysisComplete}
            />

            <TraceStep
              number="04"
              text="Model selected"
              active={analysisComplete}
            />

            <TraceStep
              number="05"
              text="Evidence validation"
              active={analysisComplete}
            />

            <TraceStep
              number="06"
              text="Response generated"
              active={analysisComplete}
            />

          </div>

        </section>


        {/* ================= RESULTS ================= */}

        <section className="results-section">

          <div className="section-title">

            <div>

              <p className="section-label">
                05 · AI RESULT
              </p>

              <h2>
                Analysis workspace
              </h2>

            </div>

            <span
              className={
                analysisComplete
                  ? "result-status complete"
                  : "result-status"
              }
            >
              {analysisComplete
                ? "Complete"
                : analyzing
                ? "Analyzing"
                : "Ready"}
            </span>

          </div>


          <div className="results-grid">


            {/* ================= IMAGE RESULT ================= */}

            <div className="panel visualization-panel">

              <div className="image-result">

                {!imageOne && (
                  <div className="placeholder">
                    <div className="placeholder-icon">◇</div>
                    <strong>Satellite imagery preview</strong>
                    <span>
                      Upload imagery to see the result visualization here
                    </span>
                  </div>
                )}

                {imageOne && !imageTwo && (
                  <div className="single-image-view">
                    <img
                      src={imageOneUrl}
                      alt="Satellite imagery"
                    />
                    <span className="image-badge image-badge-left">
                      IMAGE 1
                    </span>
                  </div>
                )}

                {imageOne && imageTwo && (
                  <div className="comparison-viewer">
                    <img
                      src={imageOneUrl}
                      alt="Before satellite imagery"
                      className="comparison-image comparison-before"
                    />

                    <div
                      className="comparison-after"
                      style={{ width: `${comparePosition}%` }}
                    >
                      <img
                        src={imageTwoUrl}
                        alt="After satellite imagery"
                        className="comparison-image comparison-after-image"
                      />
                    </div>

                    <span className="comparison-label comparison-label-before">
                      BEFORE
                    </span>

                    <span className="comparison-label comparison-label-after">
                      AFTER
                    </span>

                    <div
                      className="comparison-divider"
                      style={{ left: `${comparePosition}%` }}
                      aria-hidden="true"
                    >
                      <span className="comparison-handle">↔</span>
                    </div>

                    <input
                      className="comparison-range"
                      type="range"
                      min="0"
                      max="100"
                      value={comparePosition}
                      onChange={(event) =>
                        setComparePosition(Number(event.target.value))
                      }
                      aria-label="Before and after image comparison"
                    />
                  </div>
                )}
              </div>

            </div>


            {/* ================= RESULT ================= */}

            <div className="panel result-panel">

              <div className="result-header">

                <div>

                  <p className="section-label">
                    AI INTERPRETATION
                  </p>

                  <h3>

                    {analysisComplete
                      ? "Satellite imagery analyzed"
                      : "Waiting for analysis"}

                  </h3>

                </div>


                <div className="confidence">

                  <strong>
                    {confidence !== null
                      ? `${confidence}%`
                      : "—"}
                  </strong>

                  <span>
                    confidence
                  </span>

                </div>

              </div>


              {/* REAL AI ANSWER */}

              <p className="result-description">

                {analysisComplete
                  ? analysisResult?.answer ||
                    "Analysis completed successfully."
                  : "Upload imagery and submit a natural-language query to see the AI-generated result."}

              </p>


              {/* ================= EVIDENCE ================= */}

              <div className="evidence-section">

                <div className="sub-heading">

                  <h4>
                    Evidence
                  </h4>

                  <span>
                    {analysisComplete
                      ? `${analysisResult?.evidence?.length || 0} findings`
                      : "0 findings"}
                  </span>

                </div>


                {!analysisComplete && (

                  <div className="empty-state">
                    Evidence will appear after analysis.
                  </div>

                )}


                {analysisComplete && (

                  <div className="evidence-list">

                    {analysisResult?.evidence?.length > 0 ? (

                      analysisResult.evidence.map(
                        (item, index) => (

                          <EvidenceItem
                            key={item.id || index}
                            text={
                              item.description ||
                              item.label ||
                              "Visual evidence identified"
                            }
                            confidence={
                              item.confidence != null
                                ? `${Math.round(
                                    item.confidence * 100
                                  )}%`
                                : "—"
                            }
                          />

                        )
                      )

                    ) : (

                      <div className="empty-state">
                        No reliable evidence regions were identified.
                      </div>

                    )}

                  </div>

                )}

              </div>


              {/* ================= AI DETAILS ================= */}

              {analysisComplete && (

                <div className="change-story">

                  <div className="sub-heading">

                    <h4>
                      Analysis details
                    </h4>

                  </div>


                  <div className="story">

                    <StoryStep
                      index="01"
                      title="Task"
                      text={
                        analysisResult?.analysisType ||
                        analysisResult?.requestedAnalysisType ||
                        "AI analysis"
                      }
                    />

                    <StoryStep
                      index="02"
                      title="Model"
                      text={
                        typeof analysisResult?.model === "string"
                          ? analysisResult.model
                          : analysisResult?.model?.name ||
                            "Model information unavailable"
                      }
                    />

                    <StoryStep
                      index="03"
                      title="Evidence"
                      text={
                        analysisResult?.evidence?.length
                          ? `${analysisResult.evidence.length} visual regions identified`
                          : "No reliable regions identified"
                      }
                    />

                  </div>


                  {analysisResult?.explanation && (

                    <div className="empty-state">

                      <strong>
                        Explanation
                      </strong>

                      <br />

                      {analysisResult.explanation}

                    </div>

                  )}

                </div>

              )}

            </div>

          </div>

        </section>


        {/* ================= REPORT ================= */}

        <section
          className="lower-section"
          id="reports"
        >

          <div className="panel report-panel">

            <p className="section-label">
              REPORTING
            </p>

            <h2>
              Generate analysis report
            </h2>

            <p>
              Package the actual query, AI result, model,
              confidence, explanation and evidence into a
              structured analysis report.
            </p>

            <div className="report-buttons">

              <button
                className="primary-button"
                onClick={generateReport}
                disabled={!analysisComplete}
              >
                {reportGenerated ? "Regenerate Report" : "Generate Report"}
              </button>

              <button
                className="secondary-button"
                onClick={() => {
                  if (!analysisComplete || !analysisResult) {
                    setError("Run an analysis before previewing a report.");
                    return;
                  }
                  setReportPreviewOpen(true);
                }}
                disabled={!analysisComplete}
              >
                Preview
              </button>

            </div>

            {analysisComplete && reportGenerated && (
              <div className="report-actions" style={{
                display: "flex",
                gap: "10px",
                flexWrap: "wrap",
                marginTop: "14px"
              }}>
                <button
                  className="secondary-button"
                  onClick={printReport}
                >
                  Print / Save as PDF
                </button>

                <button
                  className="secondary-button"
                  onClick={downloadReport}
                >
                  Download HTML Report
                </button>
              </div>
            )}

            {!analysisComplete && (
              <p className="demo-text" style={{ marginTop: "12px" }}>
                Complete an analysis first to generate a report.
              </p>
            )}

          </div>


          {/* ================= HISTORY ================= */}

          <div
            className="panel history-panel"
            id="history"
          >

            <p className="section-label">
              HISTORY
            </p>

            <h2>
              Recent analyses
            </h2>

            <div className="history-empty">

              <strong>
                No analyses yet
              </strong>

              <span>
                Completed analyses will appear here.
              </span>

            </div>

          </div>

        </section>

      </main>


      {/* ================= REPORT PREVIEW ================= */}

      {reportPreviewOpen && analysisResult && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="SatQuery AI report preview"
          onClick={(event) => {
            if (event.target === event.currentTarget) {
              setReportPreviewOpen(false);
            }
          }}
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 9999,
            background: "rgba(18, 22, 19, 0.72)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "24px",
            overflowY: "auto"
          }}
        >
          <div
            style={{
              width: "min(900px, 100%)",
              maxHeight: "92vh",
              overflowY: "auto",
              background: "#fff",
              borderRadius: "12px",
              padding: "32px",
              color: "#1f2521",
              boxShadow: "0 24px 80px rgba(0,0,0,.3)"
            }}
          >
            <div style={{
              display: "flex",
              justifyContent: "space-between",
              gap: "20px",
              alignItems: "flex-start",
              borderBottom: "1px solid #ddd",
              paddingBottom: "18px"
            }}>
              <div>
                <p className="section-label">SATQUERY AI · REPORT</p>
                <h2 style={{ margin: "6px 0" }}>
                  Satellite Analysis Report
                </h2>
                <p style={{ margin: 0, opacity: 0.65 }}>
                  Generated {getReportDate()}
                </p>
              </div>

              <button
                className="secondary-button"
                onClick={() => setReportPreviewOpen(false)}
              >
                Close
              </button>
            </div>

            <div style={{ marginTop: "24px" }}>
              <p className="section-label">QUERY</p>
              <div style={{
                padding: "16px",
                background: "#f5f3ed",
                borderRadius: "8px",
                lineHeight: 1.6
              }}>
                {query}
              </div>
            </div>

            <div style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
              gap: "10px",
              marginTop: "18px"
            }}>
              <ReportMeta label="Task" value={getTaskName()} />
              <ReportMeta label="Model" value={getModelName()} />
              <ReportMeta
                label="Input"
                value={imageTwo ? "Dual imagery" : "Single imagery"}
              />
              <ReportMeta
                label="Confidence"
                value={
                  analysisResult.confidence != null
                    ? `${Math.round(analysisResult.confidence * 100)}%`
                    : confidence !== null
                      ? `${confidence}%`
                      : "—"
                }
              />
            </div>

            <div style={{ marginTop: "26px" }}>
              <p className="section-label">AI INTERPRETATION</p>
              <h3>Result</h3>
              <p style={{ lineHeight: 1.7 }}>
                {analysisResult.answer || "No answer returned."}
              </p>
            </div>

            {analysisResult.explanation && (
              <div style={{ marginTop: "24px" }}>
                <p className="section-label">EXPLANATION</p>
                <div style={{
                  border: "1px solid #ddd",
                  borderRadius: "8px",
                  padding: "16px",
                  lineHeight: 1.6
                }}>
                  {analysisResult.explanation}
                </div>
              </div>
            )}

            <div style={{ marginTop: "26px" }}>
              <p className="section-label">EVIDENCE</p>

              {analysisResult.evidence?.length > 0 ? (
                <div style={{
                  display: "grid",
                  gap: "10px"
                }}>
                  {analysisResult.evidence.map((item, index) => (
                    <div
                      key={item.id || index}
                      style={{
                        border: "1px solid #ddd",
                        borderRadius: "8px",
                        padding: "14px 16px"
                      }}
                    >
                      <strong>
                        {item.description ||
                          item.label ||
                          `Evidence region ${index + 1}`}
                      </strong>

                      <div style={{
                        marginTop: "5px",
                        fontSize: "12px",
                        opacity: 0.65
                      }}>
                        Region: x={item.x ?? "—"}, y={item.y ?? "—"},
                        width={item.width ?? "—"}, height={item.height ?? "—"}
                        {item.confidence != null &&
                          ` · confidence ${Math.round(item.confidence * 100)}%`}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p style={{ opacity: 0.65 }}>
                  No reliable evidence regions were identified.
                </p>
              )}
            </div>

            <div style={{
              display: "flex",
              gap: "10px",
              flexWrap: "wrap",
              marginTop: "30px",
              paddingTop: "20px",
              borderTop: "1px solid #ddd"
            }}>
              <button
                className="primary-button"
                onClick={printReport}
              >
                Print / Save as PDF
              </button>

              <button
                className="secondary-button"
                onClick={downloadReport}
              >
                Download HTML Report
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ================= FOOTER ================= */}

      <footer>

        <strong>
          SatQuery AI
        </strong>

        <span>
          Natural-language satellite intelligence
        </span>

      </footer>

    </div>
  );
}


/* ================= COMPONENTS ================= */

function TraceStep({ number, text, active }) {
  return (
    <div
      className={
        active
          ? "trace-step active"
          : "trace-step"
      }
    >

      <div className="trace-number">
        {number}
      </div>

      <span>
        {text}
      </span>

    </div>
  );
}


function EvidenceItem({ text, confidence }) {
  return (
    <div className="evidence-item">

      <span>
        {text}
      </span>

      <strong>
        {confidence}
      </strong>

    </div>
  );
}


function StoryStep({ index, title, text }) {
  return (
    <div className="story-step">
      <div className="story-marker">{index}</div>

      <div className="story-content">
        <strong>{title}</strong>
        <span>{text}</span>
      </div>
    </div>
  );
}


function ReportMeta({ label, value }) {
  return (
    <div style={{
      border: "1px solid #ddd",
      borderRadius: "8px",
      padding: "13px 14px",
      background: "#faf9f6"
    }}>
      <span style={{
        display: "block",
        fontSize: "10px",
        letterSpacing: "1px",
        textTransform: "uppercase",
        opacity: 0.6
      }}>
        {label}
      </span>
      <strong style={{
        display: "block",
        marginTop: "5px",
        fontSize: "13px",
        wordBreak: "break-word"
      }}>
        {value}
      </strong>
    </div>
  );
}

export default App;

