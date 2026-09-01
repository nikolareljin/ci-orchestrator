def call(Map args = [:]) {
    ciorch(args + [adapter: 'go', matrix: 'go-standard'])
}
