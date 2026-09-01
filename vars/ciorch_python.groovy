def call(Map args = [:]) {
    ciorch(args + [adapter: 'python', matrix: 'python-standard'])
}
