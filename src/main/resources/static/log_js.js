(function() {
	const urlParams = new URLSearchParams(window.location.search);
	let limit = urlParams.get("limit");
	
	fetch("/logblocks" + (limit ? "?limit=" + limit : ""))
		.then(response => response.json())
		.then(json => processJson(json))
		.catch(ex => console.error("Single request", ex));
	
	function processJson(json) {
		let logsContainer = document.getElementById("log");
		let today = new Date();
		for (let log of json) {
			let currentLogContainer = document.createElement("div");
			let unimportantLines = 0;
			
			for (let logLine of log) {
    			let currentLogLineTimeContainer = document.createElement("span");
    			let currentLogLineInfoContainer = document.createElement("span");
    			
    			let i = logLine.indexOf('] ');
    			let timestamp = logLine.slice(0, i + 1);
    			let text = logLine.slice(i + 2);
    			currentLogLineTimeContainer.innerHTML = timestamp;
    			currentLogLineInfoContainer.innerHTML = text;
    			
    			let currentLogLine = document.createElement("div");
				if (!text.startsWith(" [") && !text.startsWith("Adding to playlist") && !text.includes(" new song")) {
    				currentLogLine.classList.add("unimportant");
					unimportantLines++;
				}
				
    			currentLogLine.appendChild(currentLogLineTimeContainer);
    			currentLogLine.appendChild(currentLogLineInfoContainer);
    			currentLogContainer.appendChild(currentLogLine);
			}
			currentLogContainer.onclick = () => {
				if (currentLogContainer.classList.contains("active")) {
					currentLogContainer.classList.remove("active");		    					
				} else {
					currentLogContainer.classList.add("active");
				}
			};
			
			let blockDate = new Date(currentLogContainer.querySelector("span").innerHTML.slice(1, 11));
			if (blockDate.getDay() === today.getDay()
				&& blockDate.getMonth() === today.getMonth()
				&& blockDate.getFullYear() === today.getFullYear()) {
				currentLogContainer.classList.add("active");
			}
			
			logsContainer.appendChild(currentLogContainer);
			
			if (unimportantLines > 0) {
    			let lineCount = document.createElement("div");
    			lineCount.classList.add("linecount");
    			currentLogContainer.appendChild(lineCount);
			 	let timestamp = log[0].slice(0, log[0].indexOf('] ') + 1);
    			lineCount.innerHTML = `${timestamp} ... ${unimportantLines} line${unimportantLines !== 1 ? 's' : ''} hidden ...`;
    		}
		}
	}

	document.getElementById("copyright-currentyear").innerHTML = new Date().getFullYear().toString();

})();