def call(Map args = [:]) {
    ciorch([adapter: 'python'] + args)
}
