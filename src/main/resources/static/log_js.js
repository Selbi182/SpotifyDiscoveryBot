(function() {
	document.getElementById("copyright-current-year").innerHTML = new Date().getFullYear().toString();

	const urlParams = new URLSearchParams(window.location.search);
	let limit = urlParams.get("limit") ?? 10;

	let moreLogs = document.getElementById("more-logs");
	moreLogs.onclick = () => {
		const parser = new URL(window.location);
		parser.searchParams.set("limit", (parseInt(limit) + 10).toString());
		window.location = parser.href;
	}
	
	fetch(`/logblocks?limit=${limit}`)
		.then(response => response.json())
		.then(json => processLogBlocks(json))
		.catch(ex => console.error("Error while fetching logs", ex));
	
	function processLogBlocks(logs) {
		if (logs.length < limit) {
			moreLogs.classList.add("no-more");
			moreLogs.innerHTML = "All Logs Loaded";
		}

		let logsContainer = document.getElementById("log");
		let today = new Date();
		for (let log of logs) {
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
    			lineCount.classList.add("line-count");
    			currentLogContainer.appendChild(lineCount);
			 	let timestamp = log[0].slice(0, log[0].indexOf('] ') + 1);
    			lineCount.innerHTML = `${timestamp} ... ${unimportantLines} line${unimportantLines !== 1 ? 's' : ''} hidden ...`;
    		}
		}
	}
})();