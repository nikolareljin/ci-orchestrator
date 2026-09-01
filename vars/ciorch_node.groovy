def call(Map args = [:]) {
    ciorch(args + [adapter: 'node', matrix: 'node-standard'])
}
