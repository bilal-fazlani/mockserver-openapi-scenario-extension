(function(window) {
  window.MockServerOpenApiScenarioData = {{scenarioDataJson}};

  function scenariosFromOperationBlock(block) {
    var methodElement = block.querySelector(".opblock-summary-method");
    var pathElement = block.querySelector(".opblock-summary-path");
    if (!methodElement || !pathElement) {
      return [];
    }

    var method = methodElement.textContent.trim().toUpperCase();
    var path = pathElement.getAttribute("data-path") || pathElement.textContent.trim();
    var operationData = window.MockServerOpenApiScenarioData[method + " " + path];
    if (!operationData) {
      return null;
    }
    return Array.isArray(operationData.scenarios) ? operationData.scenarios : [];
  }

  function summarizeMatcher(matcher) {
    if (!matcher || Object.keys(matcher).length === 0) {
      return "Default fallback";
    }
    if (matcher.body && matcher.body.type === "JSON_PATH" && matcher.body.jsonPath) {
      return "body JSONPath " + matcher.body.jsonPath;
    }
    if (matcher.pathParameters) {
      return "path parameters " + Object.keys(matcher.pathParameters).join(", ");
    }
    if (matcher.queryStringParameters) {
      return "query string parameters " + Object.keys(matcher.queryStringParameters).join(", ");
    }
    if (matcher.headers) {
      return "headers " + Object.keys(matcher.headers).join(", ");
    }
    if (matcher.cookies) {
      return "cookies " + Object.keys(matcher.cookies).join(", ");
    }
    return "custom MockServer matcher";
  }

  function responseName(scenario) {
    return scenario.response || "(missing response)";
  }

  function appendText(parent, tag, className, text) {
    var element = document.createElement(tag);
    if (className) {
      element.className = className;
    }
    element.textContent = text;
    parent.appendChild(element);
    return element;
  }

  function renderScenario(scenario, index) {
    var row = document.createElement("div");
    row.className = "mockserver-scenario-row";
    row.setAttribute("data-mockserver-scenario-index", String(index));
    var prefix = scenario.matcher ? "when " + summarizeMatcher(scenario.matcher) : "default";
    appendText(row, "span", "mockserver-scenario-expression", prefix);
    appendText(row, "span", "mockserver-scenario-arrow", " -> ");
    appendText(row, "span", "mockserver-scenario-response", responseName(scenario));
    return row;
  }

  function renderPanel(scenarios) {
    var panel = document.createElement("section");
    panel.className = "mockserver-scenarios-panel";
    panel.setAttribute("data-mockserver-scenarios-panel", "true");

    if (scenarios === null) {
      appendText(panel, "div", "mockserver-scenario-empty", "No MockServer scenario mapping");
      return panel;
    }
    if (scenarios.length === 0) {
      appendText(panel, "div", "mockserver-scenario-empty", "No MockServer scenario mapping");
      return panel;
    }

    scenarios.forEach(function(scenario, index) {
      panel.appendChild(renderScenario(scenario, index));
    });
    return panel;
  }

  function injectPanels() {
    var blocks = document.querySelectorAll(".opblock");
    blocks.forEach(function(block) {
      var responses = block.querySelector(".responses-wrapper");
      if (!responses || block.querySelector("[data-mockserver-scenarios-panel='true']")) {
        return;
      }

      var scenarios = scenariosFromOperationBlock(block);
      responses.insertAdjacentElement("afterend", renderPanel(scenarios));
    });
  }

  function startRenderer() {
    injectPanels();
    var observer = new MutationObserver(function() {
      injectPanels();
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", startRenderer);
  } else {
    startRenderer();
  }

  window.MockServerOpenApiScenarios = function() {
    return {};
  };
})(window);
