// 예고 장판은 서버 예고 시스템이 그린다 → 범위 모양을 그대로 그리는 채움·외곽선 레이어는 빼고 내보낸다.
const TELEGRAPH_MESHES = new Set(['wedge', 'wedgeEdge', 'wedgeArc', 'disc8', 'disc8Edge', 'annulus', 'annulusEdge', 'half10', 'half10Edge', 'cross', 'crossEdge',
  'lane', 'laneEdge', 'laneW', 'laneWEdge', 'cone12', 'cone12Edge', 'cone4', 'cone4Edge', 'half45', 'half45Edge', 'half16', 'half16Edge', 'lidR', 'lidL', 'lidEdges', 'lane3', 'lane3Edge']);
SCENE.systems.forEach(s => { s.layers = s.layers.filter(l => !(l.mode === 'mesh' && TELEGRAPH_MESHES.has(l.mesh))); });
