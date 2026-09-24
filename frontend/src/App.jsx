
import { useState } from "react";
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

  const [query, setQuery] = useState("");

  const [analyzing, setAnalyzing] = useState(false);
  const [analysisComplete, setAnalysisComplete] = useState(false);

  const [confidence, setConfidence] = useState(null);
  const [analysisResult, setAnalysisResult] = useState(null);
  const [error, setError] = useState("");

  const currentModel =
    MODELS.find((model) => model.name === selectedModel) || MODELS[0];

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
    setError("");
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

                <div>

                  <strong>
                    Automatic model selection
                  </strong>

                  <p>
                    SatQuery AI will understand your query,
                    identify the required task, and select
                    the appropriate specialist model automatically.
                  </p>

                </div>

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

              <div>

                <span>
                  Routing
                </span>

                <strong>
                  {analysisMode === "auto"
                    ? "Automatic"
                    : selectedModel}
                </strong>

              </div>


              <div>

                <span>
                  Input
                </span>

                <strong>
                  {imageTwo
                    ? "Dual imagery"
                    : "Single imagery"}
                </strong>

              </div>


              <div>

                <span>
                  Evidence validation
                </span>

                <strong>
                  Enabled
                </strong>

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

                    <div className="placeholder-icon">
                      ◇
                    </div>

                    <strong>
                      Satellite imagery preview
                    </strong>

                    <span>
                      Your result visualization will appear here
                    </span>

                  </div>

                )}


                {imageOne && (

                  <img
                    src={URL.createObjectURL(imageOne)}
                    alt="Satellite imagery"
                  />

                )}

              </div>


              <div className="viewer-controls">

                <button className="active">
                  Original
                </button>

                <button>
                  Evidence
                </button>

                <button>
                  Change Map
                </button>

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
                      title="Task"
                      text={
                        analysisResult?.analysisType ||
                        analysisResult?.requestedAnalysisType ||
                        "AI analysis"
                      }
                    />

                    <div className="story-line"></div>

                    <StoryStep
                      title="Model"
                      text={
                        typeof analysisResult?.model === "string"
                          ? analysisResult.model
                          : analysisResult?.model?.name ||
                            "Model information unavailable"
                      }
                    />

                    <div className="story-line"></div>

                    <StoryStep
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
              Package the query, imagery, model selection,
              result, confidence and evidence into a
              structured analysis report.
            </p>

            <div className="report-buttons">

              <button className="primary-button">
                Generate Report
              </button>

              <button className="secondary-button">
                Preview
              </button>

            </div>

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


function StoryStep({ title, text }) {
  return (
    <div className="story-step">

      <strong>
        {title}
      </strong>

      <span>
        {text}
      </span>

    </div>
  );
}


export default App;

