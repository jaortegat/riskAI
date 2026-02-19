/**
 * RiskAI Game Client
 */
class RiskAIGame {
    constructor(gameId) {
        this.gameId = gameId;
        this.playerId = localStorage.getItem('playerId');
        this.playerName = localStorage.getItem('playerName');
        this.gameState = null;
        this.selectedTerritory = null;
        this.targetTerritory = null;
        this.stompClient = null;
        this.lastLoggedTurn = 0;
        this.lastLoggedPhase = null;
        this.lastLoggedPlayerId = null;
        this.pendingFortifyLog = null;
        this.cpuFortifyTimeout = null;
        this.cpuFortifyLogged = false;
        this.attackQueue = [];
        this.processingAttackQueue = false;
        this.activeBattleHighlight = null;
        this.messageSeq = 0;
        this.pendingLogEntries = [];
        this.bypassLogBuffer = false;
        this.ownAttackTimeout = null;
        this.lastReinforcementLogEntry = null;
        
        this.init();
    }
    
    async init() {
        await this.loadGameState();
        this.connectWebSocket();
        this.setupEventListeners();
        this.renderMap();
        this.updateUI();
    }
    
    async loadGameState() {
        try {
            const response = await fetch(`/api/games/${this.gameId}`);
            if (!response.ok) {
                console.error('Error loading game state: HTTP', response.status);
                return;
            }
            this.gameState = await response.json();
            console.log('Game state loaded:', this.gameState);
        } catch (error) {
            console.error('Error loading game state:', error);
        }
    }
    
    connectWebSocket() {
        const socket = new SockJS('/ws');
        this.stompClient = Stomp.over(socket);
        this.stompClient.debug = null; // Disable debug logs
        
        this.stompClient.connect({}, (frame) => {
            console.log('WebSocket connected');
            
            // Subscribe to game updates
            this.stompClient.subscribe(`/topic/game/${this.gameId}`, (message) => {
                const data = JSON.parse(message.body);
                this.handleGameMessage(data);
            });
        }, (error) => {
            console.error('WebSocket error:', error);
            // Retry connection after 5 seconds
            setTimeout(() => this.connectWebSocket(), 5000);
        });
    }
    
    handleGameMessage(message) {
        console.log('Received message:', message.type);
        this.messageSeq++;
        
        switch (message.type) {
            case 'GAME_UPDATE':
            case 'GAME_STARTED':
                this.logCPUActions(message.payload);
                // Flush pending human fortify before turn/phase headers
                if (this.pendingFortifyLog) {
                    const pf = this.pendingFortifyLog;
                    this.logFortify(pf.playerName, pf.fromName, pf.toName, pf.armies);
                    this.pendingFortifyLog = null;
                }
                this.logPhaseChanges(message.payload);
                this.gameState = message.payload;
                this.updateUI();
                this.renderMap();
                break;
            case 'ERROR':
                if (message.payload?.playerId === this.playerId) {
                    this.showNotification(message.payload?.error || 'Action failed', 'danger');
                }
                break;
                
            case 'ATTACK_RESULT':
                this.showAttackResult(message.payload);
                break;
                
            case 'CPU_FORTIFY':
                this.showCpuFortifyInline(message.payload);
                break;
                
            case 'CPU_TURN_END':
                this.showCpuTurnEnd(message.payload);
                break;
                
            case 'PLAYER_JOINED':
                this.loadGameState().then(() => this.updateUI());
                break;
                
            case 'GAME_OVER':
                this.showGameOver(message.payload);
                break;
        }
    }
    
    setupEventListeners() {
        // Start game button
        document.getElementById('start-game-btn')?.addEventListener('click', () => this.startGame());
        
        // Add CPU button
        document.getElementById('add-cpu-btn')?.addEventListener('click', () => this.addCPU());
        
        // Place armies button
        document.getElementById('place-armies-btn')?.addEventListener('click', () => this.placeArmies());
        
        // Attack button
        document.getElementById('attack-btn')?.addEventListener('click', () => this.attack());
        
        // End attack button
        document.getElementById('end-attack-btn')?.addEventListener('click', () => this.endAttack());
        
        // Fortify button
        document.getElementById('fortify-btn')?.addEventListener('click', () => this.fortify());
        
        // Skip fortify button
        document.getElementById('skip-fortify-btn')?.addEventListener('click', () => this.skipFortify());
    }
    
    renderMap() {
        const svg = document.getElementById('riskai-map');
        if (!this.gameState || !this.gameState.territories) return;
        
        // Clear existing content
        svg.innerHTML = '';
        
        // Draw connection lines first
        this.drawConnections(svg);
        
        // Draw territories
        this.gameState.territories.forEach(territory => {
            this.drawTerritory(svg, territory);
        });
        
        // Draw continent labels
        this.drawContinentLabels(svg);
        
        // Re-apply battle highlights after SVG rebuild
        this.applyBattleHighlights();
    }
    
    drawConnections(svg) {
        const territoriesMap = new Map();
        this.gameState.territories.forEach(t => territoriesMap.set(t.territoryKey, t));
        
        const drawnConnections = new Set();
        
        this.gameState.territories.forEach(territory => {
            territory.neighborKeys.forEach(neighborKey => {
                const connectionKey = [territory.territoryKey, neighborKey].sort().join('-');
                if (drawnConnections.has(connectionKey)) return;
                drawnConnections.add(connectionKey);
                
                const neighbor = territoriesMap.get(neighborKey);
                if (!neighbor) return;
                
                // Skip very long connections (wrap-around like Alaska-Kamchatka)
                const dx = Math.abs(territory.mapX - neighbor.mapX);
                if (dx > 300) return;
                
                const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
                line.setAttribute('x1', territory.mapX);
                line.setAttribute('y1', territory.mapY);
                line.setAttribute('x2', neighbor.mapX);
                line.setAttribute('y2', neighbor.mapY);
                line.setAttribute('class', 'connection');
                svg.appendChild(line);
            });
        });
    }
    
    drawTerritory(svg, territory) {
        const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        group.setAttribute('class', 'territory');
        group.setAttribute('data-key', territory.territoryKey);
        
        // Territory circle — filled with lightened area color, bordered with owner color
        const areaColor = this.lightenColor(territory.continentColor || '#444', 0.45);
        const ownerColor = territory.ownerColor || '#555';

        const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        circle.setAttribute('cx', territory.mapX);
        circle.setAttribute('cy', territory.mapY);
        circle.setAttribute('r', 18);
        circle.setAttribute('fill', areaColor);
        circle.setAttribute('stroke', ownerColor);
        circle.setAttribute('stroke-width', territory.ownerId ? 4 : 2);
        circle.setAttribute('fill-opacity', '0.9');
        
        // Army count
        const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        text.setAttribute('x', territory.mapX);
        text.setAttribute('y', territory.mapY + 4);
        text.setAttribute('text-anchor', 'middle');
        text.setAttribute('class', 'territory-armies');
        text.textContent = territory.armies;
        
        // Territory name (full)
        const nameText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        nameText.setAttribute('x', territory.mapX);
        nameText.setAttribute('y', territory.mapY + 30);
        nameText.setAttribute('text-anchor', 'middle');
        nameText.setAttribute('class', 'territory-name');
        nameText.textContent = territory.name;
        
        group.appendChild(circle);
        group.appendChild(text);
        group.appendChild(nameText);
        
        // Click handler
        group.addEventListener('click', () => this.onTerritoryClick(territory));
        
        svg.appendChild(group);
    }
    
    abbreviateName(name) {
        if (name.length <= 10) return name;
        return name.split(' ').map(w => w[0]).join('');
    }
    
    /**
     * Lighten a hex color by mixing it with white.
     * @param {string} hex - e.g. '#FF6347'
     * @param {number} amount - 0 = no change, 1 = white
     */
    lightenColor(hex, amount) {
        hex = hex.replace('#', '');
        if (hex.length === 3) hex = hex.split('').map(c => c + c).join('');
        const r = parseInt(hex.substring(0, 2), 16);
        const g = parseInt(hex.substring(2, 4), 16);
        const b = parseInt(hex.substring(4, 6), 16);
        const lr = Math.round(r + (255 - r) * amount);
        const lg = Math.round(g + (255 - g) * amount);
        const lb = Math.round(b + (255 - b) * amount);
        return `#${lr.toString(16).padStart(2,'0')}${lg.toString(16).padStart(2,'0')}${lb.toString(16).padStart(2,'0')}`;
    }
    
    drawContinentLabels(svg) {
        // Build area info from continents data or territory fallback
        const areas = new Map();

        if (this.gameState.continents && this.gameState.continents.length > 0) {
            this.gameState.continents.forEach(c => {
                areas.set(c.continentKey, {
                    name: c.name,
                    color: c.color || 'rgba(255,255,255,0.6)',
                    bonus: c.bonusArmies
                });
            });
        }

        // Track first territory per area for label position
        const firstTerritory = new Map();
        this.gameState.territories.forEach(t => {
            const key = t.continentKey;
            if (!key) return;
            if (!firstTerritory.has(key)) {
                firstTerritory.set(key, t);
            }
            // Fallback: collect name/color from territory if continents list is empty
            if (!areas.has(key) && t.continentName) {
                areas.set(key, {
                    name: t.continentName,
                    color: t.continentColor || 'rgba(255,255,255,0.6)',
                    bonus: null
                });
            }
        });

        firstTerritory.forEach((t, key) => {
            const info = areas.get(key);
            if (!info) return;

            // Position label above the first territory of the area
            const cx = t.mapX;
            const cy = t.mapY - 28;

            const labelText = info.bonus != null ? `${info.name} (${info.bonus})` : info.name;

            const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            text.setAttribute('x', cx);
            text.setAttribute('y', cy);
            text.setAttribute('text-anchor', 'middle');
            text.setAttribute('class', 'continent-label');
            text.setAttribute('fill', info.color);
            text.textContent = labelText;
            svg.appendChild(text);
        });
    }
    
    onTerritoryClick(territory) {
        const isMyTurn = this.isMyTurn();
        const phase = this.gameState.currentPhase;
        
        if (!isMyTurn) return;
        
        if (phase === 'REINFORCEMENT') {
            if (territory.ownerId === this.playerId) {
                this.selectTerritory(territory);
                document.getElementById('place-armies-btn').disabled = false;
            }
        } else if (phase === 'ATTACK') {
            if (!this.selectedTerritory) {
                // Select attacking territory
                if (territory.ownerId === this.playerId && territory.armies > 1) {
                    this.selectTerritory(territory);
                    this.highlightAttackableTargets(territory);
                }
            } else {
                // Select target territory
                if (territory.ownerId !== this.playerId && 
                    this.selectedTerritory.neighborKeys.includes(territory.territoryKey)) {
                    this.clearAttackableHighlights();
                    this.targetTerritory = territory;
                    this.highlightTarget(territory);
                    document.getElementById('attack-btn').disabled = false;
                } else if (territory.ownerId === this.playerId) {
                    // Reselect attacker
                    this.clearSelection();
                    if (territory.armies > 1) {
                        this.selectTerritory(territory);
                        this.highlightAttackableTargets(territory);
                    }
                } else {
                    // Clicked an unreachable enemy — deselect
                    this.clearSelection();
                }
            }
        } else if (phase === 'FORTIFY') {
            if (!this.selectedTerritory) {
                if (territory.ownerId === this.playerId && territory.armies > 1) {
                    this.selectTerritory(territory);
                    this.highlightFortifyTargets(territory);
                }
            } else {
                if (territory.ownerId === this.playerId && 
                    territory.territoryKey !== this.selectedTerritory.territoryKey &&
                    this.selectedTerritory.neighborKeys.includes(territory.territoryKey)) {
                    // Valid neighbor — select as target (clear previous target first)
                    this.clearFortifyHighlights();
                    document.querySelectorAll('.territory.target').forEach(el => el.classList.remove('target'));
                    this.targetTerritory = territory;
                    this.highlightTarget(territory);
                    document.getElementById('fortify-btn').disabled = false;
                } else if (territory.ownerId === this.playerId && territory.armies > 1 &&
                           territory.territoryKey !== this.selectedTerritory.territoryKey) {
                    // Re-select a different source territory
                    this.clearSelection();
                    this.selectTerritory(territory);
                    this.highlightFortifyTargets(territory);
                } else {
                    this.clearSelection();
                }
            }
        }
    }
    
    selectTerritory(territory) {
        this.clearSelection();
        this.selectedTerritory = territory;
        
        const elem = document.querySelector(`[data-key="${territory.territoryKey}"]`);
        if (elem) {
            elem.classList.add('selected');
        }
    }
    
    highlightTarget(territory) {
        const elem = document.querySelector(`[data-key="${territory.territoryKey}"]`);
        if (elem) {
            elem.classList.add('target');
        }
    }
    
    clearSelection() {
        this.clearAttackableHighlights();
        this.clearFortifyHighlights();
        document.querySelectorAll('.territory.selected, .territory.target').forEach(elem => {
            elem.classList.remove('selected', 'target');
        });
        this.selectedTerritory = null;
        this.targetTerritory = null;
        
        document.getElementById('place-armies-btn').disabled = true;
        document.getElementById('attack-btn').disabled = true;
        document.getElementById('fortify-btn').disabled = true;
    }
    
    /**
     * Glow enemy territories that the selected attacker can reach.
     */
    highlightAttackableTargets(attacker) {
        this.clearAttackableHighlights();
        attacker.neighborKeys.forEach(neighborKey => {
            const neighbor = this.gameState.territories.find(t => t.territoryKey === neighborKey);
            if (neighbor && neighbor.ownerId !== this.playerId) {
                const elem = document.querySelector(`[data-key="${neighborKey}"]`);
                if (elem) elem.classList.add('attackable');
            }
        });
    }
    
    clearAttackableHighlights() {
        document.querySelectorAll('.territory.attackable').forEach(elem => {
            elem.classList.remove('attackable');
        });
    }
    
    highlightFortifyTargets(source) {
        this.clearFortifyHighlights();
        source.neighborKeys.forEach(neighborKey => {
            const neighbor = this.gameState.territories.find(t => t.territoryKey === neighborKey);
            if (neighbor && neighbor.ownerId === this.playerId) {
                const elem = document.querySelector(`[data-key="${neighborKey}"]`);
                if (elem) elem.classList.add('fortify-target');
            }
        });
    }
    
    clearFortifyHighlights() {
        document.querySelectorAll('.territory.fortify-target').forEach(elem => {
            elem.classList.remove('fortify-target');
        });
    }
    
    /** Convert ENUM_VALUE to "Enum Value" */
    formatEnum(value) {
        if (!value) return '';
        return value.replace(/_/g, ' ').replace(/\w\S*/g, w =>
            w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()
        );
    }
    
    updateUI() {
        if (!this.gameState) return;
        
        // Update game info
        document.getElementById('game-name').textContent = this.gameState.gameName;
        document.getElementById('game-status').textContent = this.formatEnum(this.gameState.status);
        document.getElementById('turn-number').textContent = this.gameState.turnNumber;
        document.getElementById('current-phase').textContent = this.formatEnum(this.gameState.currentPhase);

        // Update game mode display
        const modeInfo = document.getElementById('mode-info');
        const sidebarModeInfo = document.getElementById('sidebar-mode-info');
        const modeProgress = document.getElementById('mode-progress');
        if (this.gameState.gameMode) {
            const mode = this.gameState.gameMode;
            const setMode = (text, cls) => {
                [modeInfo, sidebarModeInfo].forEach(el => { if (el) { el.textContent = text; el.className = cls; } });
            };
            if (mode === 'CLASSIC') {
                setMode('Classic', 'badge bg-info me-2');
                if (modeProgress) modeProgress.textContent = '';
            } else if (mode === 'DOMINATION') {
                setMode('Domination ' + this.gameState.dominationPercent + '%', 'badge bg-info me-2');
                // Show current player's territory %
                if (this.playerId && this.gameState.players && this.gameState.totalTerritories > 0) {
                    const me = this.gameState.players.find(p => p.id === this.playerId);
                    if (me) {
                        const pct = Math.round(me.territoryCount / this.gameState.totalTerritories * 100);
                        modeProgress.textContent = `(You: ${pct}%)`;
                    } else {
                        modeProgress.textContent = '';
                    }
                } else {
                    modeProgress.textContent = '';
                }
            } else if (mode === 'TURN_LIMIT') {
                setMode('Turn Limit', 'badge bg-info me-2');
                if (modeProgress) modeProgress.textContent = `(${this.gameState.turnNumber}/${this.gameState.turnLimit})`;
            }
        }
        
        // Update players list
        this.updatePlayersList();
        
        // Update action panels
        this.updateActionPanels();
    }
    
    updatePlayersList() {
        const container = document.getElementById('players-list');
        container.innerHTML = this.gameState.players.map(player => {
            const isCurrentTurn = this.gameState.currentPlayer?.id === player.id;
            return `
                <div class="player-card ${isCurrentTurn ? 'current-turn' : ''} ${player.eliminated ? 'eliminated' : ''}" 
                     style="border-color: ${player.colorHex}">
                    <div class="player-name" style="color: ${player.colorHex}">
                        ${player.type === 'CPU' ? '🤖' : '👤'} ${player.name}
                        ${isCurrentTurn ? '◀' : ''}
                    </div>
                    <div class="player-stats">
                        Territories: ${player.territoryCount} | Armies: ${player.totalArmies}
                    </div>
                    ${player.eliminated ? '<span class="badge bg-danger">Eliminated</span>' : ''}
                </div>
            `;
        }).join('');
    }
    
    updateActionPanels() {
        const status = this.gameState.status;
        const phase = this.gameState.currentPhase;
        const isMyTurn = this.isMyTurn();
        
        // Hide all panels first
        document.querySelectorAll('.action-section').forEach(el => el.classList.add('d-none'));
        document.getElementById('waiting-room').classList.add('d-none');
        
        if (status === 'WAITING_FOR_PLAYERS') {
            document.getElementById('waiting-room').classList.remove('d-none');
            const canStart = this.gameState.players.length >= 2;
            document.getElementById('start-game-btn').disabled = !canStart;
            const isFull = this.gameState.maxPlayers && this.gameState.players.length >= this.gameState.maxPlayers;
            const addCpuBtn = document.getElementById('add-cpu-btn');
            if (addCpuBtn) addCpuBtn.classList.toggle('d-none', isFull);
        } else if (status === 'IN_PROGRESS') {
            // Clear CPU info panels when turn changes
            const cpuActionInfo = document.getElementById('cpu-action-info');
            if (cpuActionInfo) { cpuActionInfo.classList.add('d-none'); cpuActionInfo.innerHTML = ''; }
            if (isMyTurn) {
                if (phase === 'REINFORCEMENT') {
                    document.getElementById('reinforce-panel').classList.remove('d-none');
                    document.getElementById('reinforcements-remaining').textContent = 
                        this.gameState.reinforcementsRemaining;
                    document.getElementById('reinforce-amount').max = this.gameState.reinforcementsRemaining;
                } else if (phase === 'ATTACK') {
                    document.getElementById('attack-panel').classList.remove('d-none');
                } else if (phase === 'FORTIFY') {
                    document.getElementById('fortify-panel').classList.remove('d-none');
                }
            } else {
                document.getElementById('waiting-turn').classList.remove('d-none');
                document.getElementById('current-player-name').textContent = 
                    this.gameState.currentPlayer?.name || 'other player';
            }
        }
    }
    
    isMyTurn() {
        return this.gameState.currentPlayer?.id === this.playerId;
    }
    
    // API Actions
    async startGame() {
        try {
            const response = await fetch(`/api/games/${this.gameId}/start`, { method: 'POST' });
            if (!response.ok) {
                const body = await response.json().catch(() => null);
                this.showNotification(body?.error || 'Cannot start game', 'danger');
            }
        } catch (error) {
            this.showNotification('Connection error while starting game', 'danger');
        }
    }
    
    async addCPU() {
        try {
            const response = await fetch(`/api/games/${this.gameId}/cpu?difficulty=MEDIUM`, { method: 'POST' });
            if (!response.ok) {
                const body = await response.json().catch(() => null);
                this.showNotification(body?.error || 'Failed to add CPU player', 'danger');
                return;
            }
            await this.loadGameState();
            this.updateUI();
        } catch (error) {
            this.showNotification('Connection error while adding CPU player', 'danger');
        }
    }
    
    async placeArmies() {
        if (!this.selectedTerritory) return;
        
        const amount = parseInt(document.getElementById('reinforce-amount').value);
        const tName = this.selectedTerritory.name;
        const tKey = this.selectedTerritory.territoryKey;
        
        // Log immediately — WebSocket GAME_UPDATE may arrive before HTTP response
        const logEntry = this.logReinforcement(this.playerName, tName, amount);
        
        try {
            const response = await fetch(`/api/games/${this.gameId}/reinforce?` + new URLSearchParams({
                playerId: this.playerId,
                territoryKey: tKey,
                armies: amount
            }), { method: 'POST' });
            
            if (!response.ok) {
                const body = await response.json().catch(() => null);
                // Remove the log entry since the action failed
                if (logEntry && logEntry.parentNode) {
                    logEntry.parentNode.removeChild(logEntry);
                }
                this.showNotification(body?.error || 'Cannot place armies here', 'danger');
                return;
            }
            this.clearSelection();
        } catch (error) {
            // Remove the log entry since the action failed
            if (logEntry && logEntry.parentNode) {
                logEntry.parentNode.removeChild(logEntry);
            }
            this.showNotification('Connection error while placing armies', 'danger');
        }
    }
    
    async attack() {
        if (!this.selectedTerritory || !this.targetTerritory) return;
        
        const armies = parseInt(document.getElementById('attack-armies').value);
        const fromKey = this.selectedTerritory.territoryKey;
        const toKey = this.targetTerritory.territoryKey;
        
        try {
            const response = await fetch(`/api/games/${this.gameId}/attack?` + new URLSearchParams({
                playerId: this.playerId,
                fromTerritoryKey: fromKey,
                toTerritoryKey: toKey,
                armies: armies
            }), { method: 'POST' });
            
            if (response.ok) {
                // Attack result will arrive via WebSocket ATTACK_RESULT for all players
                await response.json(); // consume body
            } else {
                const body = await response.json().catch(() => null);
                this.showNotification(body?.error || 'Attack not allowed', 'danger');
            }
            
            this.clearSelection();
        } catch (error) {
            this.showNotification('Connection error during attack', 'danger');
        }
    }
    
    async endAttack() {
        try {
            const response = await fetch(`/api/games/${this.gameId}/endAttack?playerId=${this.playerId}`, { 
                method: 'POST' 
            });
            if (!response.ok) {
                const body = await response.json().catch(() => null);
                this.showNotification(body?.error || 'Cannot end attack phase', 'danger');
                return;
            }
            this.clearSelection();
        } catch (error) {
            this.showNotification('Connection error while ending attack', 'danger');
        }
    }
    
    async fortify() {
        if (!this.selectedTerritory || !this.targetTerritory) return;
        
        const armies = parseInt(document.getElementById('fortify-amount').value);
        const fromKey = this.selectedTerritory.territoryKey;
        const toKey = this.targetTerritory.territoryKey;
        const fromName = this.selectedTerritory.name;
        const toName = this.targetTerritory.name;
        
        // Store pending log so GAME_UPDATE can flush it before turn headers
        this.pendingFortifyLog = { playerName: this.playerName, fromName, toName, armies };
        
        try {
            const response = await fetch(`/api/games/${this.gameId}/fortify?` + new URLSearchParams({
                playerId: this.playerId,
                fromTerritoryKey: fromKey,
                toTerritoryKey: toKey,
                armies: armies
            }), { method: 'POST' });
            
            if (!response.ok) {
                const body = await response.json().catch(() => null);
                this.pendingFortifyLog = null;
                this.showNotification(body?.error || 'Fortify not allowed', 'danger');
                return;
            }
            // If GAME_UPDATE already flushed the pending log, don't double-log
            if (this.pendingFortifyLog) {
                this.logFortify(this.playerName, fromName, toName, armies);
                this.pendingFortifyLog = null;
            }
            this.clearSelection();
        } catch (error) {
            this.pendingFortifyLog = null;
            this.showNotification('Connection error during fortify', 'danger');
        }
    }
    
    async skipFortify() {
        try {
            const response = await fetch(`/api/games/${this.gameId}/skipFortify?playerId=${this.playerId}`, { 
                method: 'POST' 
            });
            if (!response.ok) {
                const body = await response.json().catch(() => null);
                this.showNotification(body?.error || 'Cannot skip fortify', 'danger');
                return;
            }
            this.clearSelection();
        } catch (error) {
            this.showNotification('Connection error while skipping fortify', 'danger');
        }
    }
    
    showAttackResult(result) {
        const fromT = this.gameState.territories.find(t => t.territoryKey === result.fromTerritory);
        const toT = this.gameState.territories.find(t => t.territoryKey === result.toTerritory);
        const fromName = fromT?.name || result.fromTerritory || '?';
        const toName = toT?.name || result.toTerritory || '?';
        
        // For human player's own attacks: log + display immediately
        if (this.isMyTurn()) {
            this.bypassLogBuffer = true;
            this.logAttack(fromName, toName, result);
            this.bypassLogBuffer = false;
            this.showBattleInPanel(fromName, toName, result);
            this.activeBattleHighlight = { fromKey: result.fromTerritory, toKey: result.toTerritory };
            this.applyBattleHighlights();
            // Clear highlight after 2s so conquered territory shows new owner color
            if (this.ownAttackTimeout) clearTimeout(this.ownAttackTimeout);
            this.ownAttackTimeout = setTimeout(() => {
                this.activeBattleHighlight = null;
                this.applyBattleHighlights();
            }, 2000);
            return;
        }
        
        // Queue non-own attacks: visual + log synced when dequeued
        this.attackQueue.push({ result, fromName, toName, seq: this.messageSeq });
        if (!this.processingAttackQueue) {
            this.processingAttackQueue = true;
            this.processAttackQueue();
        }
    }
    
    processAttackQueue() {
        if (this.attackQueue.length === 0) {
            this.processingAttackQueue = false;
            this.activeBattleHighlight = null;
            this.applyBattleHighlights();
            const panel = document.getElementById('last-battle');
            if (panel) { panel.classList.add('d-none'); panel.innerHTML = ''; }
            // Flush any remaining buffered log entries (fortify, next turn headers, etc.)
            this.flushPendingLogEntries();
            return;
        }
        
        const { result, fromName, toName, seq } = this.attackQueue.shift();
        
        // Flush buffered log entries that arrived before this attack
        this.flushPendingEntriesBefore(seq);
        
        // Log the attack now (synced with visual display)
        this.bypassLogBuffer = true;
        this.logAttack(fromName, toName, result);
        this.bypassLogBuffer = false;
        
        this.showBattleInPanel(fromName, toName, result);
        
        // Highlight territories on the map
        this.activeBattleHighlight = { fromKey: result.fromTerritory, toKey: result.toTerritory };
        this.applyBattleHighlights();
        
        // Hold for 3.5 seconds, then process next
        setTimeout(() => this.processAttackQueue(), 3500);
    }
    
    applyBattleHighlights() {
        document.querySelectorAll('.territory.battle-attacker, .territory.battle-defender').forEach(elem => {
            elem.classList.remove('battle-attacker', 'battle-defender');
        });
        if (this.activeBattleHighlight) {
            const { fromKey, toKey } = this.activeBattleHighlight;
            const fromElem = document.querySelector(`[data-key="${fromKey}"]`);
            const toElem = document.querySelector(`[data-key="${toKey}"]`);
            if (fromElem) fromElem.classList.add('battle-attacker');
            if (toElem) toElem.classList.add('battle-defender');
        }
    }
    
    showBattleInPanel(fromName, toName, result) {
        const panel = document.getElementById('last-battle');
        if (!panel) return;
        panel.classList.remove('d-none');
        
        const aDice = result.attackerDice.map(d => `<span class="die-sm attacker">${d}</span>`).join('');
        const dDice = result.defenderDice.map(d => `<span class="die-sm defender">${d}</span>`).join('');
        
        let extra = '';
        if (result.conquered) extra += '<div class="text-warning small">\u{1F3F4} Conquered!</div>';
        if (result.eliminatedPlayer) extra += `<div class="text-danger small">\u{1F480} ${result.eliminatedPlayer} eliminated!</div>`;
        
        panel.innerHTML = `
            <div class="battle-inline">
                <div class="small text-center mb-1">
                    <span class="text-danger fw-bold">${fromName}</span>
                    <span class="text-muted"> \u2694\uFE0F </span>
                    <span class="text-info fw-bold">${toName}</span>
                </div>
                <div class="d-flex justify-content-center gap-2 mb-1">
                    <div>${aDice}</div>
                    <div class="text-muted">vs</div>
                    <div>${dDice}</div>
                </div>
                <div class="text-center small text-white-50">\u2694\uFE0F \u2212${result.attackerLosses}  \u{1F6E1}\uFE0F \u2212${result.defenderLosses}</div>
                ${extra}
            </div>
        `;
    }
    
    showCpuFortifyInline(payload) {
        // Log CPU fortify to game log (mark flag to prevent double-logging from territory diff)
        this.cpuFortifyLogged = true;
        this.logFortify(payload.playerName, payload.fromTerritory, payload.toTerritory, payload.armies);
        
        const infoDiv = document.getElementById('cpu-action-info');
        if (!infoDiv) return;
        if (this.cpuFortifyTimeout) clearTimeout(this.cpuFortifyTimeout);
        infoDiv.classList.remove('d-none');
        infoDiv.innerHTML = `
            <div class="text-info">\u{1F3F0} <strong>${payload.playerName}</strong> fortified</div>
            <div class="text-warning">${payload.fromTerritory} \u2192 ${payload.toTerritory}</div>
            <div class="text-white">${payload.armies} armies moved</div>
        `;
        this.cpuFortifyTimeout = setTimeout(() => {
            infoDiv.classList.add('d-none');
            infoDiv.innerHTML = '';
        }, 4000);
    }
    
    showCpuTurnEnd(playerName) {
        const panel = document.getElementById('cpu-turn-end');
        if (!panel) return;
        panel.classList.remove('d-none');
        panel.innerHTML = `<span class="text-success">\u2705</span> <strong>${playerName}</strong> finished their turn`;
        // Auto-clear after 4 seconds
        setTimeout(() => {
            panel.classList.add('d-none');
            panel.innerHTML = '';
        }, 4000);
    }
    
    // ─── Game Log ───────────────────────────────────────────
    
    /**
     * Detect CPU reinforcement/fortify actions by comparing territory states.
     */
    logCPUActions(newState) {
        if (!this.gameState || !newState || !newState.territories) return;
        // Only log diffs for CPU player turns (not our own)
        if (this.isMyTurn()) return;
        
        const oldTerritories = new Map();
        this.gameState.territories.forEach(t => oldTerritories.set(t.territoryKey, t));
        
        const phase = this.gameState.currentPhase;
        const cpuName = this.gameState.currentPlayer?.name || 'CPU';
        
        if (phase === 'REINFORCEMENT') {
            // Find territories that gained armies
            newState.territories.forEach(t => {
                const old = oldTerritories.get(t.territoryKey);
                if (old && t.armies > old.armies) {
                    const diff = t.armies - old.armies;
                    this.logReinforcement(cpuName, t.name, diff);
                }
            });
        } else if (phase === 'FORTIFY') {
            // Fallback detection via territory diff (in case CPU_FORTIFY message was missed)
            if (!this.cpuFortifyLogged) {
                let fromName = null, toName = null, armies = 0;
                newState.territories.forEach(t => {
                    const old = oldTerritories.get(t.territoryKey);
                    if (old && t.armies < old.armies) {
                        fromName = t.name;
                        armies = old.armies - t.armies;
                    }
                    if (old && t.armies > old.armies) {
                        toName = t.name;
                    }
                });
                if (fromName && toName && armies > 0) {
                    this.logFortify(cpuName, fromName, toName, armies);
                } else {
                    this.addLogEntry(`${cpuName} skipped fortification`, 'fortify');
                }
            }
            this.cpuFortifyLogged = false;
        }
    }
    
    /**
     * Detect phase/turn changes from a new game state and log them.
     */
    logPhaseChanges(newState) {
        if (!newState) return;
        
        const turn = newState.turnNumber || 0;
        const phase = newState.currentPhase;
        const playerName = newState.currentPlayer?.name || '?';
        const playerId = newState.currentPlayer?.id || null;
        
        // Log new turn header when turn number or current player changes
        if (turn > 0 && (turn !== this.lastLoggedTurn || playerId !== this.lastLoggedPlayerId)) {
            this.lastLoggedTurn = turn;
            this.lastLoggedPlayerId = playerId;
            this.lastLoggedPhase = null;
            this.addLogEntry(`── Turn ${turn}: ${playerName} ──`, 'phase', true);
        }
        
        // Log phase change
        if (phase && phase !== this.lastLoggedPhase) {
            this.lastLoggedPhase = phase;
            if (phase === 'REINFORCEMENT') {
                const reinforcements = newState.reinforcementsRemaining || 0;
                this.addLogEntry(`📦 Reinforcement phase (${reinforcements} armies)`, 'phase');
            } else if (phase === 'ATTACK') {
                this.addLogEntry(`⚔️ Attack phase`, 'phase');
            } else if (phase === 'FORTIFY') {
                this.addLogEntry(`🏰 Fortify phase`, 'phase');
            }
        }
    }
    
    /**
     * Log a reinforcement action.
     * Returns the log entry DOM element for potential removal on error.
     */
    logReinforcement(playerName, territoryName, armies) {
        const text = `${playerName} placed ${armies} army on ${territoryName}`;
        return this.addLogEntry(text, 'reinforce');
    }
    
    /**
     * Log an attack action.
     */
    logAttack(fromName, toName, result) {
        let text = `⚔️ ${fromName} → ${toName} (−${result.attackerLosses} / −${result.defenderLosses})`;
        this.addLogEntry(text, 'attack');
        if (result.conquered) {
            this.addLogEntry(`🏴 ${toName} conquered!`, 'conquered');
        }
        if (result.eliminatedPlayer) {
            this.addLogEntry(`💀 ${result.eliminatedPlayer} eliminated!`, 'eliminated');
        }
    }
    
    /**
     * Log a fortify action.
     */
    logFortify(playerName, fromName, toName, armies) {
        this.addLogEntry(`${playerName} moved ${armies} from ${fromName} → ${toName}`, 'fortify');
    }
    
    /**
     * Add an entry to the game log.
     * Buffers entries while attack queue is processing (unless bypassed).
     * Returns the DOM element if added directly, null if buffered.
     */
    addLogEntry(text, type = 'phase', isHeader = false) {
        if (this.processingAttackQueue && !this.bypassLogBuffer) {
            this.pendingLogEntries.push({ text, type, isHeader, seq: this.messageSeq });
            return null;
        }
        return this.appendLogEntry(text, type, isHeader);
    }
    
    /**
     * Append a log entry directly to the DOM.
     * Returns the created DOM element.
     */
    appendLogEntry(text, type = 'phase', isHeader = false) {
        const logDiv = document.getElementById('game-log');
        if (!logDiv) return null;
        
        const entry = document.createElement('div');
        if (isHeader) {
            entry.className = 'log-turn-header';
        } else {
            entry.className = `log-entry log-${type}`;
        }
        entry.textContent = text;
        logDiv.appendChild(entry);
        
        // Auto-scroll to bottom
        logDiv.scrollTop = logDiv.scrollHeight;
        
        // Trim old entries (keep last 100)
        while (logDiv.children.length > 100) {
            logDiv.removeChild(logDiv.firstChild);
        }
        
        return entry;
    }
    
    /**
     * Flush buffered log entries that arrived before the given sequence number.
     */
    flushPendingEntriesBefore(seq) {
        const toFlush = [];
        const remaining = [];
        for (const entry of this.pendingLogEntries) {
            if (entry.seq < seq) {
                toFlush.push(entry);
            } else {
                remaining.push(entry);
            }
        }
        this.pendingLogEntries = remaining;
        toFlush.forEach(e => this.appendLogEntry(e.text, e.type, e.isHeader));
    }
    
    /**
     * Flush all remaining buffered log entries.
     */
    flushPendingLogEntries() {
        const entries = this.pendingLogEntries;
        this.pendingLogEntries = [];
        entries.forEach(e => this.appendLogEntry(e.text, e.type, e.isHeader));
    }
    
    /**
     * Show a notification message in the right panel.
     * @param {string} message - The message to display
     * @param {'danger'|'warning'|'info'|'success'} type - Bootstrap alert type
     * @param {number} duration - Auto-dismiss in ms (default 5000)
     */
    showNotification(message, type = 'info', duration = 5000) {
        const area = document.getElementById('notification-area');
        const alert = document.createElement('div');
        alert.className = `alert alert-${type} alert-dismissible fade show py-2 px-3 mb-2`;
        alert.setAttribute('role', 'alert');
        alert.innerHTML = `
            <small>${message}</small>
            <button type="button" class="btn-close btn-close-white p-1" data-bs-dismiss="alert"></button>
        `;
        area.appendChild(alert);
        
        // Auto-dismiss
        if (duration > 0) {
            setTimeout(() => {
                alert.classList.remove('show');
                setTimeout(() => alert.remove(), 150);
            }, duration);
        }
    }
    
    showGameOver(winnerName) {
        document.getElementById('winner-name').textContent = winnerName;
        const winMsg = document.getElementById('win-message');
        if (this.gameState && this.gameState.gameMode === 'DOMINATION') {
            winMsg.textContent = `Dominated ${this.gameState.dominationPercent}% of the map!`;
        } else if (this.gameState && this.gameState.gameMode === 'TURN_LIMIT') {
            winMsg.textContent = 'Controls the most territories when time ran out!';
        } else {
            winMsg.textContent = 'Has conquered the world!';
        }

        // Build stats table from players
        const statsBody = document.getElementById('final-stats-body');
        if (statsBody && this.gameState && this.gameState.players) {
            const sorted = [...this.gameState.players].sort((a, b) => b.territoryCount - a.territoryCount);
            statsBody.innerHTML = sorted.map((p, i) => `
                <tr class="${p.name === winnerName ? 'table-active fw-bold' : ''} ${p.eliminated ? 'text-decoration-line-through opacity-50' : ''}">
                    <td>${i === 0 ? '👑' : i + 1}</td>
                    <td><span style="color:${p.colorHex}">${p.type === 'CPU' ? '🤖' : '👤'} ${p.name}</span></td>
                    <td>${p.territoryCount}</td>
                    <td>${p.totalArmies}</td>
                </tr>
            `).join('');
        }

        // Set winner color accent
        const winnerPlayer = this.gameState?.players?.find(p => p.name === winnerName);
        const accent = winnerPlayer?.colorHex || '#ffd700';
        const header = document.querySelector('#game-over-modal .game-over-header');
        if (header) header.style.background = `linear-gradient(135deg, ${accent}33 0%, ${accent}11 100%)`;
        const trophy = document.getElementById('trophy-icon');
        if (trophy) trophy.style.color = accent;

        const modal = new bootstrap.Modal(document.getElementById('game-over-modal'));
        modal.show();
    }
}

// Initialize game when page loads
document.addEventListener('DOMContentLoaded', () => {
    if (typeof gameId !== 'undefined') {
        window.riskGame = new RiskAIGame(gameId);
    }
});
